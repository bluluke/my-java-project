package com.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

/**
 * Hello world!
 *
 */
public class App 
{
    private static String jdbcUrl;
    private static String username;
    private static String password;
    public static void main( String[] args )
    {
       


         Properties properties = new Properties();
        String filePath = "src/main/resources/database.properties";
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            properties.load(reader);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        jdbcUrl = properties.getProperty("db.url");
        username = properties.getProperty("db.username");
        password = properties.getProperty("db.password");
    }
}
