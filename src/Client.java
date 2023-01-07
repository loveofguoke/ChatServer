import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

public class Client extends Thread {
  private SocketChannel socket;
  private SelectionKey sk;
  final int MAXIN = 1024;
  // final int MAXOUT = 1024;
  // ByteBuffer in = ByteBuffer.allocate(MAXIN);
  // ByteBuffer out = ByteBuffer.allocate(MAXOUT);
  // private boolean running = true;
  ArrayList<ClientListener> lstListener = new ArrayList<ClientListener>();

  public Client(Selector sel, SocketChannel socket) {
    try {
      this.socket = socket;
      socket.configureBlocking(false);
      sk = socket.register(sel, 0);
      sk.attach(this);
      sk.interestOps(SelectionKey.OP_READ);
      sel.wakeup();
    } catch (IOException e) {

    }
  }

  // public void startListen() {
  //   start();
  // }

  // public void stopRx() {
  //   running = false;
  // }

  @Override
  public void run() {
    try {
      // String str = in.readLine();
      // in.clear();
      // socket.read(in);
      ByteBuffer in = ByteBuffer.allocate(MAXIN);
      socket.read(in);
      String str = new String(in.array());
      // System.out.println("####1"+str+"####2");
      notifyMsgRcvd(str);
      if (str.equals("END")) {
        System.out.println("closing...");
        quit();
      }
      // System.out.println("Echoing: " + str);
      // out.println(str);
    } catch (IOException e) {
      quit();
    } 
  }

  synchronized void sendMsg(String line) {
    try {
      socket.write(ByteBuffer.wrap(line.getBytes()));
    } catch (IOException e) {
      quit();
    }
  }

  void quit() {
    notifyQuit();
    sk.cancel();
    try {
      socket.close();
    } catch (IOException e1) {}
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