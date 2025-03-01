package com.example;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

@SpringBootApplication
public class TcpChatApplication implements CommandLineRunner {

    private static int PORT;
    private static String REMOTE_HOST;
    private static int REMOTE_PORT;
    private volatile boolean running = true;
    private volatile ServerSocket serverSocket; // To allow closing from outside the server thread
    private volatile Socket clientSocket; // To allow closing active client connection

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java -jar tcp-chat.jar <local-port> <remote-host> <remote-port>");
            System.exit(1);
        }

        PORT = Integer.parseInt(args[0]);
        REMOTE_HOST = args[1];
        REMOTE_PORT = Integer.parseInt(args[2]);

        SpringApplication.run(TcpChatApplication.class, args);
    }

    @Override
    public void run(String... args) {
        // Start server thread
        Thread serverThread = new Thread(this::startServer);
        serverThread.start();
        // Start client thread
        Thread clientThread = new Thread(this::startClient);
        clientThread.start();

        // Wait for client thread to finish (i.e., user types 'exit')
        try {
            clientThread.join();
            running = false; // Signal server to stop
            // Immediately close server resources
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // Force accept() to exit
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close(); // Stop reading from active connection
            }
            serverThread.join(); // Wait for server to shut down and print its message
            System.out.println("Application terminated");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Main thread interrupted");
            running = false;
        } catch (IOException e) {
            System.out.println("Error closing server resources: " + e.getMessage());
        }
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server listening on port " + PORT);
            while (running) {
                clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();
                    System.out.println("Friend connected to server");
                    try (
                            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
                    ) {
                        String inputLine;
                        while (running && (inputLine = in.readLine()) != null) {
                            System.out.println("Friend: " + inputLine);
                        }
                        System.out.println("Friend disconnected");
                    }
                } catch (IOException e) {
                    if (running) {
                        System.out.println("Server error with client: " + e.getMessage());
                    }
                } finally {
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        try {
                            clientSocket.close();
                        } catch (IOException e) {
                            System.out.println("Error closing client socket: " + e.getMessage());
                        }
                    }
                    clientSocket = null; // Reset reference
                }
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("Server startup/shutdown error: " + e.getMessage());
            }
        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.out.println("Error closing server socket: " + e.getMessage());
                }
            }
            System.out.println("Server shutting down"); // Always print this on shutdown
        }
    }

    private void startClient() {
        Socket socket = null;
        boolean connected = false;

        // Retry logic
        while (running && !connected) {
            try {
                socket = new Socket(REMOTE_HOST, REMOTE_PORT);
                connected = true;
            } catch (IOException e) {
                System.out.println("Failed to connect to " + REMOTE_HOST + ":" + REMOTE_PORT + ". Retrying in 2 seconds...");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.out.println("Client interrupted");
                    return;
                }
            }
        }

        if (!running) return;

        // Once connected, proceed with the chat
        try (
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Connected to friend at " + REMOTE_HOST + ":" + REMOTE_PORT);
            System.out.println("Start typing messages (type 'exit' to quit):");

            String message;
            while (running && scanner.hasNextLine()) {
                message = scanner.nextLine();
                if ("exit".equalsIgnoreCase(message)) {
                    running = false;
                    break;
                }
                out.println(message);
                //System.out.println("You: " + message);
            }
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println("Error closing socket: " + e.getMessage());
                }
            }
            System.out.println("Client shutting down");
        }
    }
}