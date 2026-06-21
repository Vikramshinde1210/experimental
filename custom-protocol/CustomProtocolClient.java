import java.io.*;
import java.net.*;

public class CustomProtocolClient {

    public static void main(String[] args) throws Exception {

        Socket socket =
                new Socket("localhost", 8080);

        DataOutputStream out =
                new DataOutputStream(socket.getOutputStream());

        send(out, (byte)1, "vikram");
        send(out, (byte)2, "hello server");
        send(out, (byte)3, "");
    }

    private static void send(
            DataOutputStream out,
            byte type,
            String msg
    ) throws IOException {

        byte[] payload = msg.getBytes();

        out.writeInt(payload.length);
        out.writeByte(type);
        out.write(payload);

        out.flush();
    }
}