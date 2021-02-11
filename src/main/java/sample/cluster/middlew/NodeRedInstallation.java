package sample.cluster.middlew;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import com.fasterxml.jackson.annotation.JsonCreator;
import sample.cluster.CborSerializable;


public class NodeRedInstallation extends AbstractBehavior<NodeRedInstallation.Event> {

    interface Event extends CborSerializable {}

    /**
     * Messages(events) that the actor can handle
     **/
    public static final class ReceiveInput implements Event {
        public final Object phase;
        public final int step;
        public final Object result;
        public final ActorRef<ReturnOutput> replyTo;

        public ReceiveInput(Object input, int step, Object result, ActorRef<ReturnOutput> replyTo) {
            this.phase = input;
            this.step = step;
            this.result = result;
            this.replyTo = replyTo;
        }
    }

    public static final class ReturnOutput implements Event {
        public final Object output;

        @JsonCreator
        public ReturnOutput(Object output) {
            this.output = output;
        }
    }

    public static final class SetUp implements Event {
        public final int portIN;
        public final int portOUT;

        /**
         * @param portOUT this the port bounded to the TCP-IN node in NodeRED
         * @param portIN this the port bounded to the TCP-OUT node in NodeRED
         */
        public SetUp(int portOUT, int portIN) {
            this.portOUT = portOUT;
            this.portIN = portIN;
        }
    }


    /**
     * Constructor
     **/
    public NodeRedInstallation(ActorContext<Event> context) {
        super(context);
        context.getLog().info("Registering your Node-Red installation to the repository");
        context.getSystem().receptionist().tell(Receptionist.register(NODERED_SERVICE_KEY, context.getSelf().narrow()));
    }

    public static Behavior<NodeRedInstallation.Event> create() {
        return Behaviors.setup(NodeRedInstallation::new);
    }


    /**
     * Variables and actor state
     **/
    public static ServiceKey<NodeRedInstallation.ReceiveInput> NODERED_SERVICE_KEY =
            ServiceKey.create(NodeRedInstallation.ReceiveInput.class, "NodeRedInstallation");
    private ActorRef<NodeRedBroker.Event> myBroker;
    public int PORTIN, PORTOUT;

    @Override
    public Receive<Event> createReceive() {
        return newReceiveBuilder()
                .onMessage(ReceiveInput.class, this::onInputReceived)
                .onMessage(SetUp.class, this::onSetUp)
                .build();
    }


    /**
     * Behaviors of the actor
     **/
    private Behavior<Event> onInputReceived(ReceiveInput event) {
        if ((event.phase.toString().equals("UPDATE"))){
            // Send the update to the local broker associated to the installation
            event.replyTo.tell(new ReturnOutput("UPDATE_REQUEST_RECEIVED"));
            myBroker.tell(new NodeRedBroker.Update(event.step, event.result));
        }
        return this;
    }

    // This method set the ports for the socket
    private Behavior<Event> onSetUp(SetUp event){
        PORTIN = event.portIN;
        PORTOUT = event.portOUT;

        myBroker = getContext().spawn(NodeRedBroker.create(), "NodeRedBroker");
        myBroker.tell(new NodeRedBroker.SetUp(PORTOUT, PORTIN));
        getContext().getLog().info("Create your local broker, with socket ports:\nPortOUT = {}\nPortIN = {}", PORTOUT, PORTIN);

        return this;
    }
}
