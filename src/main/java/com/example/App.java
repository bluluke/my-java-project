package com.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


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
            server.createContext("/create", new CreateHandler());   
            server.createContext("/add_flashcard/", new AddFlashcardHandler());
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

    static class CreateHandler implements HttpHandler {
        @Override 
        public void handle(HttpExchange exchange) throws IOException {
            if("POST".equals(exchange.getRequestMethod())) {
                try {
                    // Extract data from the request body
                    InputStream requestBody = exchange.getRequestBody();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody));
                    String line;
                    StringBuilder requestBodyContent = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        requestBodyContent.append(line);
                    }
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode requestData = objectMapper.readTree(requestBodyContent.toString());
                    String newName = requestData.get("flash_cards_name").asText();
                    

                
                    // This establishes the connection with the database
                try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                    
                    String sql = "CREATE TABLE " + newName + " (id INT PRIMARY KEY, name VARCHAR(255))";
                    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                        preparedStatement.executeUpdate();
                    }
                    System.out.println("Table '" + newName + "' created successfully.");

                    
                    String response = "{\"status\": \"success\", \"message\": \"Table '" + newName + "' created successfully.\"}";
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }


                } catch (SQLException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, 0);  
                }

                } catch (IOException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(400, 0);  
                    
                }
            }
        }
    }

    static class AddFlashcardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                 
                    String[] pathParts = exchange.getRequestURI().getPath().split("/");
                    String tableName = pathParts[pathParts.length - 1]; 
                    
                    InputStream requestBody = exchange.getRequestBody();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody));
                    StringBuilder requestBodyContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        requestBodyContent.append(line);
                    }
                    
                   
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode requestData = objectMapper.readTree(requestBodyContent.toString());
                    
                    
                    String question = requestData.get("question").asText();
                    String answer = requestData.get("answer").asText();
                    
                   
                    try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                        
                        String sql = "INSERT INTO " + tableName + " (question, answer) VALUES (?, ?)";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                            preparedStatement.setString(1, question);
                            preparedStatement.setString(2, answer);
                            preparedStatement.executeUpdate();
                        }
                        
                     
                        String response = "{\"status\": \"success\", \"message\": \"Flashcard added successfully.\"}";
                        exchange.sendResponseHeaders(200, response.getBytes().length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                        exchange.sendResponseHeaders(500, 0);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(400, 0);
                }
            }
        }
    }
    

    
    }

