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

    /** Messages(events) that the actor can handle **/
    public static final class ReceiveInput implements NodeRedInstallation.Event {
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


    /** Variables and actor state **/
    public static ServiceKey<NodeRedInstallation.ReceiveInput> NODERED_SERVICE_KEY =
            ServiceKey.create(NodeRedInstallation.ReceiveInput.class, "NodeRedInstallation");
    private final ActorRef<NodeRedBroker.Event> myBroker;
    //public int PORTIN, PORTOUT;


    /** Constructor **/
    public NodeRedInstallation(ActorContext<Event> context) {
        super(context);
        context.getLog().info("Registering your Node-Red installation to the repository");
        context.getSystem().receptionist().tell(Receptionist.register(NODERED_SERVICE_KEY, context.getSelf().narrow()));

        myBroker = getContext().spawn(NodeRedBroker.create(), "NodeRedBroker");
        getContext().getLog().info("Create your local broker");
    }

    public static Behavior<NodeRedInstallation.Event> create() {
        return Behaviors.setup(NodeRedInstallation::new);
    }


    @Override
    public Receive<Event> createReceive() {
        return newReceiveBuilder().onMessage(ReceiveInput.class, this::onInputReceived).build();
    }


    private Behavior<Event> onInputReceived(ReceiveInput event) {
        if ((event.phase.toString().equals("UPDATE"))){
            // Send the update to the local broker associated to the installation
            event.replyTo.tell(new ReturnOutput("UPDATE_REQUEST_RECEIVED"));
            myBroker.tell(new NodeRedBroker.Update(event.step, event.result));
        }
        return this;
    }
}
