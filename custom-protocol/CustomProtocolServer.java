import java.io.*;
import java.net.*;

public class CustomProtocolServer {

    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {

        ServerSocket serverSocket = new ServerSocket(PORT);

        System.out.println("Server started on port " + PORT);

        while (true) {

            Socket socket = serverSocket.accept();

            new Thread(() -> handleClient(socket)).start();
        }
    }

    private static void handleClient(Socket socket) {

        try (
                DataInputStream in =
                        new DataInputStream(socket.getInputStream());

                DataOutputStream out =
                        new DataOutputStream(socket.getOutputStream())
        ) {

            while (true) {

                // 4-byte message length
                int length = in.readInt();

                // 1-byte message type
                byte type = in.readByte();

                byte[] payload = new byte[length];

                in.readFully(payload);

                String message = new String(payload);

                System.out.println(
                        "Type=" + type +
                        " Message=" + message
                );

                processMessage(type, message, out);
            }

        } catch (Exception e) {

            System.out.println("Client disconnected");
        }
    }

    private static void processMessage(
            byte type,
            String message,
            DataOutputStream out
    ) throws IOException {

        switch (type) {

            case 1:
                System.out.println("LOGIN -> " + message);
                sendResponse(out, (byte)100, "LOGIN SUCCESS");
                break;

            case 2:
                System.out.println("CHAT -> " + message);
                sendResponse(out, (byte)101, "MESSAGE RECEIVED");
                break;

            case 3:
                System.out.println("HEARTBEAT");
                sendResponse(out, (byte)102, "ALIVE");
                break;

            default:
                sendResponse(out, (byte)103, "UNKNOWN TYPE");
        }
    }

    private static void sendResponse(
            DataOutputStream out,
            byte type,
            String response
    ) throws IOException {

        byte[] payload = response.getBytes();

        out.writeInt(payload.length);
        out.writeByte(type);
        out.write(payload);

        out.flush();
    }
}