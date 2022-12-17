import java.io.*;
import java.net.*;
import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;

interface ClientListener {
  void msgRcvd(String msg);
  void clientQuit(Client c);
}

public class Server implements ClientListener, Runnable {
  public static final int PORT = 3936;
  LinkedList<Client> lstClient = new LinkedList<Client>();
  private Queue<String> msgQueue = new ArrayDeque<String>();
  private ExecutorService executer = Executors.newCachedThreadPool();

  private Server() {}
  private static Server theServer = new Server();

  protected static Server getServer() {
    return theServer;
  }
  synchronized void sendMsg(String msg) {
    // for (Client c : lstClient) {
    //   c.sendMsg(msg);
    // }
    msgQueue.add(msg);
    this.notify();
  }

  @Override
  public void msgRcvd(String msg) {
      sendMsg(msg);
  }

  @Override
  public void clientQuit(Client c) {
      lstClient.remove(c);
      c.stopRx();
  }
  
  void go() throws IOException {
    ServerSocket s = new ServerSocket(PORT);
    System.out.println("Started: " + s);
    try { // 无论try块里是否出现异常, 都能保证s被close
      Runnable queueRunner = this;
      executer.submit(queueRunner);
      while (true) {
        // Blocks until a connection occurs
        Socket socket = s.accept();
        // System.out.println("Connection accepted: " + socket);
        Client c = new Client(socket);
        c.addMsgListener(this);
        lstClient.add(c);
        // c.startListen();
        executer.submit(c);
      }
    } catch (IOException e) {
    } finally {
      executer.shutdown();
      s.close();
    }
  } 

  @Override
  public void run() {
      while (true) {
        synchronized (this) {
          try {
            while (msgQueue.isEmpty()) {
              this.wait();
            }
            for (String msg : msgQueue) {
              for (Client c : lstClient) {
                // May require new thread
                // c.sendMsg(msg);
                executer.submit(new Sender(c, msg));
              }
              msgQueue.poll(); // right?
            }
          } catch (InterruptedException e) {}
        }
      }
  }
  
  public static void main(String[] args) throws IOException {
    theServer.go();
  }
}

class Sender implements Runnable {
  Client c;
  String msg;

  Sender(Client c, String msg) {
    this.c = c;
    this.msg = msg;
  }

  @Override
  public void run() {
      c.sendMsg(msg);
  }
}