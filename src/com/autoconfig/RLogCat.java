package com.autoconfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class RLogCat {

    public static class StreamReaderRunnable implements Runnable {

        private InputStream inputStream;

        private String[] requestPrefixes;
        private String startTag;
        private String endTag;

        private boolean isErrorStream;

        public StreamReaderRunnable(InputStream inputStream, boolean isErrorStream) {
            this.inputStream = inputStream;
            this.isErrorStream = isErrorStream;
        }

        public void setStartTag(String tag) {
            this.startTag = tag;
        }

        public void setEndTag(String tag) {
            this.endTag = tag;
        }

        public void setRequestPrefixes(String[] requestPrefixes) {
            this.requestPrefixes = requestPrefixes;
        }

        @Override
        public void run() {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line = null;
            try {
                boolean isReadingResponse = false;
                StringBuffer buffer = new StringBuffer();
                String request = null;
                System.out.println("\n");
                while ((line = bufferedReader.readLine()) != null) {
                    if(!isErrorStream) {
                        int start = line.indexOf(':');
                        if(start >= 0) {
                            line = line.substring(start + 2);

                            if(!isReadingResponse) {
                                String requestString = null;

                                if(line.trim().equals(startTag)) {
                                    isReadingResponse = true;
                                    buffer.delete(0, buffer.length());
                                }
                                else if((requestString = getRequestString(line, requestPrefixes)) != null) {
                                    request = requestString;
                                }
                            } else {
                                if(line.trim().equals(endTag)) {
                                    System.out.println(request);
                                    System.out.println(getUnderline(request.length()));
                                    String prettyResponse = getPrettyResponse(buffer.toString());
                                    System.out.println(prettyResponse);
                                    System.out.println("\n");
                                    isReadingResponse = false;
                                } else {
                                    buffer.append(line);
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getUnderline(int length) {
        StringBuffer sb = new StringBuffer(length);
        for(int i = 0; i < length; i++) {
            sb.append('-');
        }

        return sb.toString();
    }

    private static String getRequestString(String line, String[] requestPrefixes) {
        for (int i = 0; i < requestPrefixes.length; i++) {
            String prefix = requestPrefixes[i];
            if(line.startsWith(prefix)) {
                return line;
            }
        }

        return null;
    }

    private static String getPrettyResponse(String content) {
        String prettyContent = getPrettyJSON(content);
        if(prettyContent != null) {
            return prettyContent;
        }

        prettyContent = getPrettyXML(content, 4);
        if(prettyContent != null) {
            return prettyContent;
        }

        return content;
    }

    private static String getPrettyJSON(String content) {
        String prettyJSON = null;

        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(content);
            prettyJSON = gson.toJson(element);
        } catch (Exception e) {
        }

        return prettyJSON;
    }

    public static String getPrettyXML(String input, int indent) {
        String prettyXML = null;

        try {
            Source xmlInput = new StreamSource(new StringReader(input));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setAttribute("indent-number", indent);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(xmlInput, xmlOutput);
            prettyXML = xmlOutput.getWriter().toString();
        } catch (Exception e) {
        }

        return prettyXML;
    }

    public static void main(String[] args) {
        if(args.length > 0) {
            String cmdLine = args[0];
            String startTag = args[1];
            String endTag = args[2];
            String[] requestPrefixes = args[3].split("\\|");

            try {
                Runtime rt = Runtime.getRuntime();
                Process proc = rt.exec(cmdLine);
                InputStream stdOut = proc.getInputStream();
                InputStream stdError = proc.getErrorStream();

                StreamReaderRunnable stdOutReader = new StreamReaderRunnable(stdOut, false);
                stdOutReader.setStartTag(startTag);
                stdOutReader.setEndTag(endTag);
                stdOutReader.setRequestPrefixes(requestPrefixes);

                new Thread(stdOutReader).start();
                new Thread(new StreamReaderRunnable(stdError, true)).start();

                int exitVal = proc.waitFor();
                System.out.println("Process exitValue: " + exitVal);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else {
            System.out.println("Usage:\njava -jar rlogcat.jar <logcat cmd line> <start-tag> <end-tag> <request prefix list>");
            System.out.println("Example:\njava -jar rlogcat.jar \"adb logcat HttpRequestExecutor:I *:S\" \"<RESULT_BEGIN>\" \"<RESULT_END>\" \"GET|POST\"");
        }
    }
}
