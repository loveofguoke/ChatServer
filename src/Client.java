import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class Client extends Thread {
  private Socket socket;
  private BufferedReader in;
  private PrintWriter out;
  private boolean running = true;
  ArrayList<ClientListener> lstListener = new ArrayList<ClientListener>();

  public Client(Socket socket) {
    this.socket = socket;
      try {
        in =
          new BufferedReader(
            new InputStreamReader(
              socket.getInputStream()));
        // Enable auto-flush
        out = 
          new PrintWriter(
            new BufferedWriter(
              new OutputStreamWriter(
                socket.getOutputStream())), true);
      } catch (IOException e) {

      }
  }

  public void startListen() {
    start();
  }

  public void stopRx() {
    running = false;
  }

  @Override
  public void run() {
      try {
        while (running) {
          String str = in.readLine();
          notifyMsgRcvd(str);
          if (str.equals("END")) break;
          // System.out.println("Echoing: " + str);
          // out.println(str);
        }
        System.out.println("closing...");
      } catch (IOException e) {
      } finally {
        notifyQuit();
        try {
          socket.close();
        } catch (IOException e) {}
      }
  }

  synchronized void sendMsg(String line) {
    out.println(line);
  }

  public synchronized void addMsgListener(ClientListener l) {
    lstListener.add(l);
  }

  synchronized void notifyMsgRcvd(String msg) {
    for (ClientListener ml : lstListener) {
      ml.msgRcvd(msg);
    }
  }

  synchronized void notifyQuit() {
    for (ClientListener ml : lstListener) {
      ml.clientQuit(this);
    }
  }
}