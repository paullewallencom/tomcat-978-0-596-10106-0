import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * MulticastNode is a very simple program to test multicast.  It starts
 * up and joins the multicast group 228.0.0.4 on port 45564 (this is the
 * default address and port of Tomcat 6's Cluster group communications).
 * This program uses the first argument as a message to send into the
 * multicast group, and then spends the remainder of its time listening
 * for messages from other nodes and printing those messages to standard
 * output.
 */
public class MulticastNode {

    InetAddress group = null;
    MulticastSocket s = null;
    

    /**
     * Pass this program a string argument that it should send to the
     * multicast group.
     */
    public static void main(String[] args) {

        if (args.length > 0) {

            System.out.println("Sending message: " + args[0]);

            // Start up this MulticastNode
            MulticastNode node = new MulticastNode();

            // Send the message
            node.send(args[0]);

            // Listen in on the multicast group, and print all messages
            node.receive();

        } else {

            System.out.println("Need an argument string to send.");
            System.exit(1);

        }

    }


    /**
     * Construct a MulticastNode on group 228.0.0.4 and port 45564.
     */
    public MulticastNode() {

        try {

            group = InetAddress.getByName("228.0.0.4");
            s = new MulticastSocket(45564);
            s.joinGroup(group);

        } catch (Exception e) {

            e.printStackTrace();

        }
    }


    /**
     * Send a string message to the multicast group for all to see.
     *
     * @param msg the message string to send to the multicast group.
     */
    public void send(String msg) {

        try {

            DatagramPacket hi = new DatagramPacket(
                msg.getBytes(), msg.length(), group, 45564);
            s.send(hi);

        } catch (Exception e) {

            e.printStackTrace();

        }
    }


    /**
     * Loop forever, listening to the multicast group for messages sent
     * from other nodes as DatagramPackets.  When one comes in, print it
     * to standard output, then go back to listening again.
     */
    public void receive() {

        byte[] buf;

        // Loop forever
        while (true) {

            try {

                buf = new byte[1000];
                DatagramPacket recv = new DatagramPacket(buf, buf.length);
                s.receive(recv);
                System.out.println("Received: " + new String(buf));

            } catch (Exception e) {

                e.printStackTrace();

            }
        }
    }
}