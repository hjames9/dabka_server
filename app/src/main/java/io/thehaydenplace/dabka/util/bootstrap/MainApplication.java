package io.thehaydenplace.dabka.util.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

public class MainApplication
{
    private static void failedExit(String message)
    {
        exit(message, System.err, 1);
    }

    private static void successExit(String message)
    {
        exit(message, System.out, 0);
    }

    private static void exit(String message, PrintStream stream, int exitCode)
    {
        stream.println(message);
        System.exit(exitCode);
    }

    private static Booter getBooter(Properties properties)
            throws ClassNotFoundException, NoSuchMethodException, InstantiationException,
                IllegalAccessException, InvocationTargetException
    {
        String BOOTER_CLASS_STRING = "booter.class";
        String className = properties.getProperty(BOOTER_CLASS_STRING);
        if(null == className) {
            failedExit(String.format("%s is not defined", BOOTER_CLASS_STRING));
        }

        Class<?> clazz = Class.forName(className);
        Constructor<?> ctor = clazz.getConstructor(Properties.class);
        Object object = ctor.newInstance(properties);
        if(!Booter.class.isInstance(object)) {
            failedExit(String.format("%s is not a Booter class", object.getClass().getSimpleName()));
        }

        return (Booter) object;
    }

    private static Properties getPropertiesFromURL(URL url) throws IOException
    {
        URLConnection conn = url.openConnection();
        if(!conn.getContentType().contains("application/json"))
            throw new IllegalArgumentException(String.format("Unknown content type %s", conn.getContentType()));

        InputStream stream = conn.getInputStream();
        String configJson;
        try (Scanner scanner = new Scanner(stream)) {
            configJson = scanner.useDelimiter("\\A").next();
        }

        if(!configJson.isEmpty()) {
            return convertJsonToProperties(configJson);
        } else {
            throw new IllegalArgumentException("Config JSON is empty");
        }
    }

    private static Properties convertJsonToProperties(String configJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(configJson);

        Properties properties = new Properties();

        Iterator<Map.Entry<String, JsonNode>> iterator = json.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            if(!entry.getValue().isTextual()) {
                continue;
            }

            properties.setProperty(entry.getKey(), entry.getValue().textValue());
        }

        return properties;
    }

    public static void main(String [] args)
    {
        try {
            if(args.length != 1) {
                System.out.println("Configuration parameter not provided, please use https or file URL");
                return;
            }

            URL url = new URL(args[0]);
            String scheme = url.toURI().getScheme();

            Properties properties;
            if(scheme.equalsIgnoreCase("file")) {
                properties = new Properties();
                properties.load(new FileInputStream(url.getFile()));
            } else if(scheme.startsWith("http")) {
                properties = getPropertiesFromURL(url);
            } else {
                throw new IllegalArgumentException(String.format("Unknown URL type used: %s", scheme));
            }

            System.out.println("Process coming up");

            Booter booter = getBooter(properties);
            booter.run();

            Thread thread = Thread.currentThread();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    booter.close();
                } catch(IOException e) {
                    failedExit(e.toString());
                }
                thread.interrupt();
            }));

            while(true) {
                try {
                    Thread.sleep(10000);
                } catch(InterruptedException e) {
                    break;
                }
            }

            successExit("Process shutting down");
        } catch(Exception e) {
            failedExit(e.toString());
        }
    }
}