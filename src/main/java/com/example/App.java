package com.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Properties;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

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

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8082), 0);
            server.createContext("/hello", new MyHandler());
            server.createContext("/read", new ReadHandler());
            server.start();
            System.out.println("Server started on port 8082");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "Hello, world!";
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        }
    }

    static class ReadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
        
            String uri = exchange.getRequestURI().toString();
            String[] uriParts = uri.split("/");
            String tableName = uriParts[uriParts.length - 1]; 

            try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
      
                String sql = "SELECT * FROM " + tableName;
                // ...

                try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
                ResultSet resultSet = preparedStatement.executeQuery()) {

       
                String jsonResponse = convertResultSetToJson(resultSet);

     
                exchange.sendResponseHeaders(200, jsonResponse.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes());
                }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, 0);  
            }
        }


     private String convertResultSetToJson(ResultSet resultSet) throws SQLException {
        StringBuilder response = new StringBuilder();
        response.append("[");
    

        while (resultSet.next()) {
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            int columnCount = resultSetMetaData.getColumnCount();
    
            response.append("{");
    
            for (int i = 1; i <= columnCount; i++) {
                String columnName = resultSetMetaData.getColumnName(i);
                Object value = resultSet.getObject(i);
    
                response.append("\"").append(columnName).append("\":\"").append(value).append("\",");
            }
    

            if (columnCount > 0) {
                response.deleteCharAt(response.length() - 1);
            }
    
            response.append("},");
        }
    
        if (response.length() > 1) {
            response.deleteCharAt(response.length() - 1);
        }
    
        response.append("]");
    
        return response.toString();
    }
    
    }

    }

