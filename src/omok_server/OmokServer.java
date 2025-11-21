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
    private Vector<GameRoom> rooms = new Vector<>();

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
        SwingUtilities.invokeLater(() -> {
            textArea.append(message + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    public void removeRoom(GameRoom room) {
        synchronized (rooms) {
            if (rooms.remove(room)) {
                printDisplay(room.getTitle() +" 방이 제거됨 ");
            }
        }
    }

    public class ClientHandler extends Thread {
        private Socket clientSocket;
        private String uid;
        private Player myPlayer;
        private ObjectOutputStream out;

        private GameRoom myRoom = null;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        private void receiveMessages(Socket cs) {
            try {
                out = new ObjectOutputStream(new BufferedOutputStream(cs.getOutputStream()));
                out.flush();
                ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(cs.getInputStream()));
                String message;
                OmokMsg msg;
                while ((msg = (OmokMsg) in.readObject()) != null) {
                    if (msg.getMode() == OmokMsg.MODE_LOGIN) {
                        uid = msg.getUserID();
                        printDisplay("새 참가자: " + uid);
                        printDisplay("현재 참가자수: " + users.size());
                        String allUserIds = OmokServer.this.getAllLUsers();
                        broadcastLobby(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_USER_LIST, allUserIds));
                        String allRooms = OmokServer.this.getAllRooms();
                        broadcastLobby(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_ROOM_LIST, allRooms));
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
                        String roomTitle = msg.getMessage();
                        Player owner = new Player(this);
                        this.myPlayer = owner;

                        GameRoom newRoom = new GameRoom(roomTitle, owner, OmokServer.this);
                        synchronized (OmokServer.this.rooms) {
                            OmokServer.this.rooms.add(newRoom);
                        }
                        this.myRoom = newRoom;
                        printDisplay(roomTitle + " 생성 완료");
                        String allRooms = OmokServer.this.getAllRooms();
                        broadcastLobby(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_ROOM_LIST, allRooms));
                        broadcastLobby(new OmokMsg("SERVER", OmokMsg.MODE_LOBBY_STRING, roomTitle + " 생성 완료"));
                        send(new OmokMsg("SERVER", OmokMsg.MODE_ROOM_ENTERED, newRoom.getTitle()));
                        String roomInfo = roomTitle + ", " + owner.getClientHandler().getUid();
                        send(new OmokMsg("SERVER", OmokMsg.MODE_ROOM_INFO, roomInfo));
                        newRoom.broadcastGameRoom(new OmokMsg("SERVER",OmokMsg.MODE_REFRESH_GAME_USER_LIST, newRoom.getPlayersForClient()));

                    } else if (msg.getMode() == OmokMsg.MODE_JOIN_ROOM) {
                        GameRoom gameRoom = findGameRoom(msg.getMessage());
                        this.myPlayer = new Player(this);
                        this.myRoom = gameRoom;
                        gameRoom.enterPlayer(myPlayer);
                        send(new OmokMsg("SERVER", OmokMsg.MODE_ROOM_ENTERED, msg.getMessage()));
                        String roomInfo = gameRoom.getTitle() + ", " + gameRoom.getOwner().getClientHandler().getUid();
                        send(new OmokMsg("SERVER", OmokMsg.MODE_ROOM_INFO, roomInfo));
                        gameRoom.broadcastGameRoom(new OmokMsg("SERVER",OmokMsg.MODE_REFRESH_GAME_USER_LIST, gameRoom.getPlayersForClient()));
                    } else if (msg.getMode() == OmokMsg.MODE_EXIT_ROOM) {
                        this.myRoom.exitPlayer(myPlayer);
                        if (this.myRoom.getPlayerCount() > 0) {
                            String roomInfo = this.myRoom.getTitle() + ", " + this.myRoom.getOwner().getClientHandler().getUid();
                            this.myRoom.broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_ROOM_INFO, roomInfo));
                            this.myRoom.broadcastGameRoom(new OmokMsg("SERVER",OmokMsg.MODE_REFRESH_GAME_USER_LIST, this.myRoom.getPlayersForClient()));
                        }
                        this.myRoom = null;
                        this.myPlayer = null;
                        String allRooms = OmokServer.this.getAllRooms();
                        broadcastLobby(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_ROOM_LIST, allRooms));
                        send(new OmokMsg("SERVER", OmokMsg.MODE_EXIT_ROOM, "SUCCESS"));
                    } else if(msg.getMode() == OmokMsg.MODE_WAITING_STRING) {
                        message = uid + ": " + msg.getMessage();
                        printDisplay(message);
                        this.myRoom.broadcastGameRoom(msg);
                    } else if(msg.getMode() == OmokMsg.MODE_GAME_START) {
                        if (this.myRoom.getOwner().equals(myPlayer)) {
                            this.myRoom.startGame();
                            this.myRoom.broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_START, "SUCCESS"));
                        } else {
                            send(new OmokMsg("SERVER", OmokMsg.MODE_START, "FAILED"));
                        }
                    }
                }
                users.removeElement(this);
                String allUserIds = OmokServer.this.getAllLUsers();
                broadcastLobby(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_USER_LIST, allUserIds));
                printDisplay(uid + "퇴장. 현재 참가자 수: " + users.size());

            } catch (IOException e) {
                users.remove(this);
                String allUserIds = OmokServer.this.getAllLUsers();
                broadcastLobby(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_USER_LIST, allUserIds));
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
        synchronized void send(OmokMsg msg) {
            try {
                if (out == null) {
                    return;
                }
                out.writeObject(msg);
                out.flush();
                out.reset();
            } catch (IOException e) {
                System.err.println("전송 오류 (" + uid + "): " + e.getMessage());
            }
        }

        private void broadcastLobby(OmokMsg msg) {
            for (ClientHandler user : users) {
                user.send(msg);
            }
        }

        String getUid() {
            return uid;
        }

        @Override
        public void run() {
            receiveMessages(clientSocket);
        }
    }

    public GameRoom findGameRoom(String roomId) {
        synchronized (rooms) {
            for (GameRoom room : rooms) {
                if (room.getRoomId().equals(roomId)) {
                    return room;
                }
            }
        }
        return null;
    }
    public String getAllLUsers() {
        String allLUsers = "";
        synchronized (users) {
            for (ClientHandler user : users) {
                if (!allLUsers.isEmpty()) {
                    allLUsers += ",";
                }
                allLUsers += user.getUid();
            }
        }
        return allLUsers;
    }

    public String getAllRooms() {
        String allRooms = "";
        synchronized (rooms) {
            for (GameRoom room : rooms) {
                if (!allRooms.isEmpty()) {
                    allRooms += ",";
                }
                allRooms += room.getRoomId();
                allRooms += "|";
                allRooms += room.getTitle();

            }
        }
        return allRooms;
    }

    public static void main(String[] args) {
        new OmokServer(54322);
    }
}
