package sample.cluster.middlew;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import sample.cluster.CborSerializable;
import java.io.BufferedReader;
import java.io.IOException;


public class NodeRedInputListener extends AbstractBehavior<NodeRedInputListener.Event> {

    /**
     * Messages(events) that the actor can handle
     **/
    interface Event extends CborSerializable {}

    public static final class SetUp implements Event{

        public BufferedReader in;
        public ActorRef<NodeRedBroker.Event> myBroker;

        @JsonCreator
        public SetUp(BufferedReader in, ActorRef<NodeRedBroker.Event> myBroker) {
            this.in = in;
            this.myBroker = myBroker;
        }

    }

    public static final class ReadyToReadFromSocket implements Event{}


    /**
     * Constructor
     **/
    public NodeRedInputListener(ActorContext<Event> context) {
        super(context);
    }

    public static Behavior<NodeRedInputListener.Event> create() { return Behaviors.setup(NodeRedInputListener::new);
    }


    /**
     * Actor State and variables
     **/
    ActorRef<NodeRedBroker.Event> myBroker;
    private BufferedReader in;
    private boolean isActive = true;


    @Override
    public Receive<Event> createReceive() {
        return newReceiveBuilder()
                .onMessage(NodeRedInputListener.SetUp.class, this::onSetUp)
                .onMessage(NodeRedInputListener.ReadyToReadFromSocket.class, this::readFromSocket)
                .build();
    }


    /**
     * Behaviors of the actor
     **/
    private Behavior<NodeRedInputListener.Event> onSetUp(NodeRedInputListener.SetUp event) {
        in = event.in;
        myBroker = event.myBroker;
        getContext().getSelf().tell(new ReadyToReadFromSocket());
        return this;
    }

    private Behavior<NodeRedInputListener.Event> readFromSocket(NodeRedInputListener.ReadyToReadFromSocket event) throws IOException {

        while (isActive) {
            getContext().getLog().info("Waiting for input from NodeRed");
            String line = in.readLine();
            getContext().getLog().info("Received local input from NodeRed: {}", line);

            /* Extract from the input info about the step */
            int index = line.indexOf("{");
            int nextStep = Integer.parseInt(line.substring(0, index));
            getContext().getLog().info("Next step is: {}", nextStep);

            line = line.substring(index);
            getContext().getLog().info("Input cleaned: {}", line);

            myBroker.tell(new NodeRedBroker.InternalUpdate(nextStep, line));
        }
        return this;
    }
}
