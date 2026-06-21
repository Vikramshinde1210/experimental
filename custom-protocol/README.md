# Custom Protocol over TCP in Java

A simple implementation of a **custom application-layer protocol** built directly on top of TCP using Java Sockets.

This project demonstrates how protocols such as HTTP, Redis, Kafka, MySQL, PostgreSQL, gRPC, and many others are ultimately built on top of TCP by defining their own message structure and parsing rules.

---

# Project Structure

```text
.
├── CustomProtocolServer.java
├── CustomProtocolClient.java
└── README.md
```

---

# Learning Objectives

This project demonstrates:

- Java Sockets
- TCP Communication
- Custom Protocol Design
- Length-Prefix Framing
- DataInputStream
- DataOutputStream
- Binary Protocols
- Client-Server Communication
- Message Parsing
- Application Layer Protocol Design

---

# Understanding TCP

TCP provides:

- Reliable Delivery
- Ordered Delivery
- Error Detection
- Flow Control
- Congestion Control
- Connection Management

TCP does NOT provide:

- Message Boundaries
- Request Types
- Payload Structure
- Serialization Format
- Business Logic

TCP only provides a stream of bytes.

---

# Why Do We Need Protocols?

Suppose a client sends:

```text
Hello
World
```

TCP may deliver:

```text
HelloWorld
```

or

```text
Hel
loWo
rld
```

or

```text
Hello
World
```

The receiver cannot determine:

```text
Where does Message 1 end?
Where does Message 2 begin?
```

Therefore applications must define their own protocol.

---

# What Is A Protocol?

A protocol is simply an agreement between client and server about:

```text
How bytes should be structured
How bytes should be interpreted
How messages should be processed
```

For example:

```text
First 4 bytes = Length
Next 1 byte = Type
Remaining bytes = Payload
```

If both client and server follow these rules, communication works.

---

# Custom Protocol Structure

This project uses the following packet format:

```text
+-------------+-------------+-------------+
| Length      | Type        | Payload     |
+-------------+-------------+-------------+
| 4 bytes     | 1 byte      | N bytes     |
+-------------+-------------+-------------+
```

---

# Packet Fields

| Field | Size | Description |
|---------|---------|---------|
| Length | 4 Bytes | Payload Length |
| Type | 1 Byte | Message Type |
| Payload | N Bytes | Actual Data |

---

# Message Types

| Type | Meaning |
|--------|---------|
| 1 | LOGIN |
| 2 | CHAT |
| 3 | HEARTBEAT |

---

# Example Packet

Message:

```text
Hello
```

Packet:

```text
Length = 5
Type   = 2
Payload= Hello
```

Binary:

```text
00 00 00 05
02
48 65 6C 6C 6F
```

---

# Length-Prefix Framing

Length-prefix framing solves message boundary problems.

Without Length:

```text
HelloWorld
```

Receiver cannot determine:

```text
Hello + World

OR

Hell + oWorld
```

With Length:

```text
[5][Hello]
[5][World]
```

Receiver always knows exactly how many bytes belong to each message.

---

# Server Workflow

```text
Start Server
     │
     ▼
Accept Connection
     │
     ▼
Read Length
     │
     ▼
Read Type
     │
     ▼
Read Payload
     │
     ▼
Process Request
     │
     ▼
Send Response
```

---

# Client Workflow

```text
Connect To Server
      │
      ▼
Create Packet
      │
      ▼
Write Length
      │
      ▼
Write Type
      │
      ▼
Write Payload
      │
      ▼
Flush Stream
```

---

# Sample Packet Flow

```text
CLIENT

LOGIN vikram

Length = 6
Type   = 1
Payload= vikram

          │
          ▼

TCP Stream

          │
          ▼

SERVER

Read Length
Read Type
Read Payload

Process LOGIN Request
```

---

# How Is This Possible Over TCP?

TCP only transports bytes.

Example:

```text
Client
   |
   | 00 00 00 05 02 48 65 6C 6C 6F
   |
TCP
   |
Server
```

TCP does not know:

```text
Length
Type
Payload
LOGIN
CHAT
HEARTBEAT
```

Those meanings are defined entirely by our protocol.

The client and server agree:

```text
First 4 bytes = Length
Next 1 byte = Type
Remaining bytes = Payload
```

This agreement creates a protocol.

---

# How HTTP Works

HTTP is also a protocol built on top of TCP.

Instead of defining:

```text
Length
Type
Payload
```

HTTP defines:

```text
Request Line
Headers
Body
```

Example:

```http
POST /login HTTP/1.1
Host: localhost
Content-Length: 5

Hello
```

---

# Custom Protocol vs HTTP

## Our Protocol

```text
+---------+---------+---------+
| Length  | Type    | Payload |
+---------+---------+---------+
```

Example:

```text
Length=5
Type=2
Payload=Hello
```

---

## HTTP Protocol

```http
POST /chat HTTP/1.1
Host: localhost
Content-Length: 5

Hello
```

HTTP packet contains:

```text
Request Line
Headers
Body
```

Both are simply different ways of organizing bytes.

---

# Where Is HTTP Stored?

One of the most common misconceptions is that HTTP is somehow built into TCP.

It is not.

HTTP is defined by a specification (RFC).

TCP only transports bytes.

The HTTP specification tells developers:

```text
How requests should look
How responses should look
How headers should be formatted
How bodies should be transmitted
```

---

# Who Defines HTTP?

HTTP standards are maintained by the:

- Internet Engineering Task Force (IETF)

Examples:

- HTTP/1.1
- HTTP/2
- HTTP/3

The specification is published publicly.

Browser developers and server developers independently implement it.

---

# How Client And Server Agree On HTTP

There is no central storage where HTTP is kept.

Instead:

```text
HTTP Specification
         │
         ▼
Browser Developers
         │
         ▼
Chrome / Firefox / Edge
```

and

```text
HTTP Specification
         │
         ▼
Server Developers
         │
         ▼
Tomcat / Nginx / Apache
```

Both sides read the same specification and write code accordingly.

---

# Analogy With Our Protocol

Suppose we define:

```text
First 4 bytes = Length
Next 1 byte = Type
Remaining bytes = Payload
```

Client:

```java
out.writeInt(length);
out.writeByte(type);
out.write(payload);
```

Server:

```java
int length = in.readInt();
byte type = in.readByte();
```

There is no central storage.

Both programs simply follow the same rules.

HTTP works exactly the same way.

---

# What Happens Inside Chrome?

When you enter:

```text
https://google.com
```

Chrome internally builds:

```http
GET / HTTP/1.1
Host: google.com
User-Agent: Chrome
Accept: */*
```

Then converts it to bytes and sends them through a TCP socket.

---

# What Happens Inside Tomcat?

Conceptually:

```java
ServerSocket serverSocket =
        new ServerSocket(8080);

while (true) {

    Socket socket =
            serverSocket.accept();

    HttpRequest request =
            parseHttpRequest(socket);

    Object result =
            invokeController(request);

    writeHttpResponse(
            socket,
            result
    );
}
```

Tomcat performs much more work internally, but the concept is identical to our custom protocol parser.

---

# Real Spring Boot Request Flow

```text
Browser
   │
   ▼
TCP Connection
   │
   ▼
Tomcat ServerSocket
   │
   ▼
HTTP Parser
   │
   ▼
DispatcherServlet
   │
   ▼
Controller
   │
   ▼
Service
   │
   ▼
Repository
   │
   ▼
Database
   │
   ▼
Response Object
   │
   ▼
JSON Serialization
   │
   ▼
HTTP Response
   │
   ▼
TCP Bytes
```

---

# Comparison Table

| Feature | Custom Protocol | HTTP |
|----------|----------|----------|
| Runs on TCP | ✅ | ✅ |
| Human Readable | ❌ | ✅ |
| Binary Efficient | ✅ | ❌ |
| Easy Debugging | ❌ | ✅ |
| Compact | ✅ | ❌ |
| Browser Support | ❌ | ✅ |
| Industry Standard | ❌ | ✅ |
| Extensible | ✅ | ✅ |

---

# Compile

```bash
javac CustomProtocolServer.java
javac CustomProtocolClient.java
```

---

# Run Server

```bash
java CustomProtocolServer
```

Expected Output:

```text
Server started on port 8080
```

---

# Run Client

Open another terminal:

```bash
java CustomProtocolClient
```

---

# Sample Server Output

```text
Server started on port 8080

Type=1 Message=vikram
LOGIN -> vikram

Type=2 Message=hello server
CHAT -> hello server

Type=3 Message=
HEARTBEAT

Client disconnected
```

---

# Why "Client Disconnected" Appears

After sending all packets, the client program exits.

When the JVM exits:

```text
Socket closes
        │
        ▼
Server tries next read()
        │
        ▼
EOFException
        │
        ▼
Client disconnected
```

This is normal behavior.

---

# Production-Grade Protocol Design

Real protocols often contain additional metadata:

```text
+---------+---------+---------+---------+---------+
| Version | Type    | ReqId   | Length  | Payload |
+---------+---------+---------+---------+---------+
```

Additional fields commonly include:

- Protocol Version
- Request ID
- Correlation ID
- Timestamp
- Checksum
- Compression Flag
- Encryption Flag
- Authentication Token

---

# Key Takeaway

TCP is only a reliable byte stream.

Everything above TCP—including HTTP, Redis, Kafka, MySQL, PostgreSQL, gRPC, and this project—is simply a protocol that defines:

```text
How bytes should be structured
How bytes should be interpreted
How messages should be processed
```

A custom protocol is nothing more than an agreement between client and server about the meaning of bytes flowing through a TCP connection.