package omok_server;

import omok_shared.OmokMsg;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Vector;

public class OmokServer extends JFrame {
    private int port;
    private ServerSocket serverSocket = null;
    private Vector<ClientHandler> users;

    private Thread acceptThread = null;
    private JTextArea textArea;
    private JButton connectButton;
    private JButton disconnectButton;
    private JButton exitButton;

    public OmokServer(int port) {
        super("Omok Server");

        setSize(400, 300);
        setLocationRelativeTo(null);

        buildGUI();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);

        this.port = port;
    }
    private void buildGUI() {
        add(createDisplayPanel(), BorderLayout.CENTER);
        add(createControlPanel(), BorderLayout.SOUTH);
    }
    private JPanel createDisplayPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 0));

        connectButton = new JButton("서버 시작");
        disconnectButton = new JButton("서버 종료");
        exitButton = new JButton("종료");

        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                acceptThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        startServer();
                    }
                });
                acceptThread.start();

                connectButton.setEnabled(false);
                disconnectButton.setEnabled(true);
                exitButton.setEnabled(false);
            }
        });
        disconnectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                disconnect();

                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
                exitButton.setEnabled(true);
            }
        });

        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                try {
                    if (serverSocket != null) {
                        serverSocket.close();
                    }
                } catch (IOException e) {
                    System.err.println("닫기 오류> " + e.getMessage());
                }
                System.exit(-1);
            }
        });

        panel.add(connectButton);
        panel.add(disconnectButton);
        panel.add(exitButton);

        disconnectButton.setEnabled(false);

        return panel;

    }

    private void startServer() {
        Socket clientSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            printDisplay("서버가 시작되었습니다.");
            users = new Vector<ClientHandler>();
            while (acceptThread == Thread.currentThread()) {
                clientSocket = serverSocket.accept();
                printDisplay("클라이언트가 연결되었습니다: "
                        + clientSocket.getInetAddress().getHostAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                users.add(clientHandler);

                clientHandler.start();

            }
        } catch(SocketException e) {
            printDisplay("서버 소켓 종료");
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                System.err.println("서버 닫기 오류> " + e.getMessage());
                System.exit(-1);
            }
        }
    }

    private void disconnect() {
        try {
            acceptThread = null;
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("서버 소켓 닫기 오류> " + e.getMessage());
            System.exit(-1);
        }
    }

    private void printDisplay(String message) {
        textArea.append(message + "\n");
        textArea.setCaretPosition(textArea.getDocument().getLength());
    }

    public class ClientHandler extends Thread {
        private Socket clientSocket;
        private String uid;
        private ObjectOutputStream out;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        private void receiveMessages(Socket cs) {
            try {
                ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(cs.getInputStream()));
                out = new ObjectOutputStream(new BufferedOutputStream(cs.getOutputStream()));
                String message;
                OmokMsg msg;
                while ((msg = (OmokMsg) in.readObject()) != null) {
                    if (msg.getMode() == OmokMsg.MODE_LOGIN) {
                        uid = msg.getUserID();
                        printDisplay("새 참가자: " + uid);
                        printDisplay("현재 참가자수: " + users.size());
                        continue;
                    } else if (msg.getMode() == OmokMsg.MODE_LOGOUT) {
                        break;
                    } else if (msg.getMode() == OmokMsg.MODE_LOBBY_STRING) {
                        message = uid + ": " + msg.getMessage();
                        printDisplay(message);
                        broadcastLobby(msg);
                    } else if (msg.getMode() == OmokMsg.MODE_LOBBY_IMAGE) {
                        message = uid + ": " + msg.getMessage();
                        printDisplay(message);
                        broadcastLobby(msg);
                    } else if (msg.getMode() == OmokMsg.MODE_MAKE_ROOM) {

                    }
                }
                users.removeElement(this);
                printDisplay(uid + "퇴장. 현재 참가자 수: " + users.size());

            } catch (IOException e) {
                users.remove(this);
                printDisplay("서버 읽기 오류> " + e.getMessage());
            } catch (ClassNotFoundException e) {
                printDisplay("잘못된 객체가 전달되었습니다.");
            } finally {
                try {
                    if (cs != null) {
                        cs.close();
                    }
                } catch (IOException e) {
                    System.err.println("닫기 오류> " + e.getMessage());
                    System.exit(-1);
                }
            }
        }
        private void send(OmokMsg msg) {
            try {
                out.writeObject(msg);
                out.flush();
            } catch (IOException e) {
                System.err.println("클라이언트 일반 전송 오류> " + e.getMessage());
            }
        }

        private void broadcastLobby(OmokMsg msg) {
            for (ClientHandler user : users) {
                user.send(msg);
            }
        }
        @Override
        public void run() {
            receiveMessages(clientSocket);
        }
    }
    public static void main(String[] args) {
        new OmokServer(54322);
    }
}
