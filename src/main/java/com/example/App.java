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

import io.github.cdimascio.dotenv.Dotenv;

public class App 
{
    private static String jdbcUrl;
    private static String username;
    private static String password;
    public static void main( String[] args )
    {

        Dotenv dotenv = Dotenv.load();
        jdbcUrl = dotenv.get("DB_URL");
        username = dotenv.get("DB_USERNAME");
        password = dotenv.get("DB_PASSWORD");

        Properties properties = new Properties();
        String filePath = "src/main/resources/database.properties";
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            properties.load(reader);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8082), 0);
            server.createContext("/read_table", new ReadTableHandler());
            server.createContext("/create_table", new CreateTableHandler());  
            server.createContext("/delete_flashcard/", new DeleteFlashcardHandler()); 
            server.createContext("/delete_table/", new DeleteTableHandler());
            server.createContext("/add_flashcard/", new AddFlashcardHandler());
            server.createContext("/edit_flashcard/", new EditFlashcardHandler());

            server.start();
            System.out.println("Server started on port 8082");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    static class ReadTableHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String uri = exchange.getRequestURI().toString();
            String[] uriParts = uri.split("/");
            String tableName = uriParts[uriParts.length - 1];
    
            try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                String sql = "SELECT * FROM " + tableName;
                try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
                     ResultSet resultSet = preparedStatement.executeQuery()) {
    
                    String jsonResponse = convertResultSetToJson(resultSet);
    
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
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
    
    static class CreateTableHandler implements HttpHandler {
        @Override 
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    InputStream requestBody = exchange.getRequestBody();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody));
                    StringBuilder requestBodyContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        requestBodyContent.append(line);
                    }
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode requestData = objectMapper.readTree(requestBodyContent.toString());
                    String newName = requestData.get("flash_cards_name").asText();

                    try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                        String sql = "CREATE TABLE " + newName + " (id SERIAL PRIMARY KEY, question VARCHAR(255), answer VARCHAR(255))";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                            preparedStatement.executeUpdate();
                        }
                        System.out.println("Table '" + newName + "' created successfully.");
                        
                        String response = "{\"status\": \"success\", \"message\": \"Table '" + newName + "' created successfully.\"}";

                        exchange.getResponseHeaders().set("Content-Type", "application/json");
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
    
    static class DeleteFlashcardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("DELETE".equals(exchange.getRequestMethod())) {
                try {
                    String[] pathParts = exchange.getRequestURI().getPath().split("/");
                    String tableName = pathParts[pathParts.length - 2];
                    int flashcardId;
                    
                    try {
                        flashcardId = Integer.parseInt(pathParts[pathParts.length - 1]);
                    } catch (NumberFormatException e) {
                        exchange.sendResponseHeaders(400, 0);
                        return;
                    }
                    
                    if (!isValidTableName(tableName)) {
                        exchange.sendResponseHeaders(400, 0);
                        return;
                    }
    
                    try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                        String sql = "DELETE FROM " + tableName + " WHERE id = ?";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                            preparedStatement.setInt(1, flashcardId);
                            int rowsAffected = preparedStatement.executeUpdate();
                            
                            if (rowsAffected > 0) {
                                String response = "{\"status\": \"success\", \"message\": \"Flashcard deleted successfully.\"}";
                                exchange.sendResponseHeaders(200, response.getBytes().length);
                                try (OutputStream os = exchange.getResponseBody()) {
                                    os.write(response.getBytes());
                                }
                            } else {
                                exchange.sendResponseHeaders(404, 0);
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                        exchange.sendResponseHeaders(500, 0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, 0);
                }
            }
        }
    

        private boolean isValidTableName(String tableName) {
            String regex = "^[a-zA-Z0-9_]*$";
            return tableName.matches(regex);
        }
        
    }

    static class DeleteTableHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("DELETE".equals(exchange.getRequestMethod())) {
                try {
                    String[] pathParts = exchange.getRequestURI().getPath().split("/");
                    String tableName = pathParts[pathParts.length - 1];

                    if (!isValidTableName(tableName)) {
                        exchange.sendResponseHeaders(400, 0); 
                        return;
                    }
    
                    try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                        String sql = "DROP TABLE IF EXISTS " + tableName;
                        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                            preparedStatement.executeUpdate();
    
                            String response = "{\"status\": \"success\", \"message\": \"Table deleted successfully.\"}";
                            exchange.sendResponseHeaders(200, response.getBytes().length);
                            try (OutputStream os = exchange.getResponseBody()) {
                                os.write(response.getBytes());
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                        exchange.sendResponseHeaders(500, 0);
                    }
                } catch (Exception e) {
                    exchange.sendResponseHeaders(400, 0);
                }
            }
        }
        private boolean isValidTableName(String tableName) {
            String regex = "^[a-zA-Z0-9_]*$";
            return tableName.matches(regex);
        }
    }
    
    static class EditFlashcardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("PATCH".equals(exchange.getRequestMethod())) {
                try {

                    String[] pathParts = exchange.getRequestURI().getPath().split("/");
                    String tableName = pathParts[pathParts.length - 2];
                    int flashcardId = Integer.parseInt(pathParts[pathParts.length - 1]);

                    InputStream requestBody = exchange.getRequestBody();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(requestBody));
                    StringBuilder requestBodyContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        requestBodyContent.append(line);
                    }
    
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode requestData = objectMapper.readTree(requestBodyContent.toString());
                    String updatedQuestion = requestData.get("question").asText();
                    String updatedAnswer = requestData.get("answer").asText();
    
                    try (Connection connection = DriverManager.getConnection(App.jdbcUrl, App.username, App.password)) {
                        String sql = "UPDATE " + tableName + " SET question = ?, answer = ? WHERE id = ?";
                        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                            preparedStatement.setString(1, updatedQuestion);
                            preparedStatement.setString(2, updatedAnswer);
                            preparedStatement.setInt(3, flashcardId);
                            int rowsAffected = preparedStatement.executeUpdate();

                            if (rowsAffected > 0) {
                                String response = "{\"status\": \"success\", \"message\": \"Flashcard updated successfully.\"}";
                                exchange.sendResponseHeaders(200, response.getBytes().length);
                                try (OutputStream os = exchange.getResponseBody()) {
                                    os.write(response.getBytes());
                                }
                            } else {
                                exchange.sendResponseHeaders(404, 0); 
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                        exchange.sendResponseHeaders(500, 0); 
                    }
                } catch (NumberFormatException e) {
                    exchange.sendResponseHeaders(400, 0); 
                }
            }
        }

    }
}

