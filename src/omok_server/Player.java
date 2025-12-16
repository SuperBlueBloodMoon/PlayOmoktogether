package omok_server;

public class Player {
    private OmokServer.ClientHandler client;

    public Player(OmokServer.ClientHandler client) {
        this.client = client;
    }

    // Getters
    public OmokServer.ClientHandler getClientHandler() {
        return client;
    }
}
