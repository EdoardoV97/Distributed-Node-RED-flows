package sample.cluster.middlew;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import sample.cluster.CborSerializable;
import sample.cluster.middlew.ServerSocket.ServerSocketToNodeRed;
import sample.cluster.middlew.ServerSocket.SocketObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.*;


public class NodeRedBroker extends AbstractBehavior<NodeRedBroker.Event> {

    interface Event extends CborSerializable {}

    /** Messages(events) that the actor can handle **/
    private static final class NRInstallationsUpdated implements Event {
        public final Set<ActorRef<NodeRedInstallation.ReceiveInput>> newInstallations;

        public NRInstallationsUpdated(Set<ActorRef<NodeRedInstallation.ReceiveInput>> newInstallations) {
            this.newInstallations = newInstallations;
        }
    }

    public static final class Update implements Event {
        public final int step;
        public final Object result;

        @JsonCreator
        public Update(int step, Object result) {
            this.step = step;
            this.result = result;
        }
    }

    public static final class SetUp implements Event {
        public final int portOUT;
        public final int portIN;

        /**
         * @param portOUT this the port bounded to the TCP-IN node in NodeRED
         * @param portIN this the port bounded to the TCP-OUT node in NodeRED
         */
        public SetUp(int portOUT, int portIN) {
            this.portOUT = portOUT;
            this.portIN = portIN;
        }
    }

    public static final class UpdateSuccessful implements Event{}
    public static final class UpdateFailed implements Event{}


    /** Constructor **/
    public NodeRedBroker(ActorContext<Event> context) {
        super(context);
        ActorRef<Receptionist.Listing> subscriptionAdapter =
                context.messageAdapter(Receptionist.Listing.class, listing ->
                        new NodeRedBroker.NRInstallationsUpdated(listing.getServiceInstances(NodeRedInstallation.NODERED_SERVICE_KEY)));

        context.getSystem().receptionist().tell(Receptionist.subscribe(NodeRedInstallation.NODERED_SERVICE_KEY, subscriptionAdapter));
    }

    public static Behavior<NodeRedBroker.Event> create() {
        return Behaviors.setup(NodeRedBroker::new);
    }


    /** Actor State and variables **/
    // This list represent the repository
    private final List<ActorRef<NodeRedInstallation.ReceiveInput>> runningInstallations = new ArrayList<>();
    int PORTOUT; // To send input to NodeRed
    int PORTIN; // To receive output from NodeRed
    ServerSocketToNodeRed nodeRedSocketOUT;
    ServerSocketToNodeRed nodeRedSocketIN;


    @Override
    public Receive<NodeRedBroker.Event> createReceive() {
        return newReceiveBuilder()
                .onMessage(NodeRedBroker.NRInstallationsUpdated.class, this::onInstallationUpdated)
                .onMessage(NodeRedBroker.Update.class, this::onUpdateReceived)
                .onMessage(NodeRedBroker.SetUp.class, this::onSetUp)
                .onMessage(NodeRedBroker.UpdateSuccessful.class, this::noBehavior)
                .onMessage(NodeRedBroker.UpdateFailed.class, this::noBehavior)
                .build();
    }

    private Behavior<Event> noBehavior(Event event) {
        /* Do nothing */
        return this;
    }

    private Behavior<NodeRedBroker.Event> onInstallationUpdated(NodeRedBroker.NRInstallationsUpdated update) {
        runningInstallations.clear();
        runningInstallations.addAll(update.newInstallations);
        return this;
    }

    // This method set the ports for the socket
    private Behavior<Event> onSetUp(SetUp event){
        PORTOUT = event.portOUT;
        PORTIN = event.portIN;

        nodeRedSocketOUT = new ServerSocketToNodeRed(PORTOUT);
        nodeRedSocketIN = new ServerSocketToNodeRed(PORTIN);
        return this;
    }


    private Behavior<Event> onUpdateReceived (NodeRedBroker.Update event) throws IOException {

        ObjectOutputStream out = nodeRedSocketOUT.getOut();
        BufferedReader in = nodeRedSocketIN.getIn();

        // Convert Object to JSON to send to NodeRed
        getContext().getLog().info("Received update: {}", event.result);
        ObjectMapper mapper = new ObjectMapper();

        SocketObject socketObject = new SocketObject(event.step, event.result);
        String json;
        json = mapper.writeValueAsString(socketObject);
        getContext().getLog().info("Update converted in JSON: {}", json);


        // Send the input to NodeRed
        out.writeObject(json);
        out.flush();
        getContext().getLog().info("Successfully send input to NodeRed");


        //Receive the output from NodeRed
        //TODO aggiungere Thread o attore per leggere input all'infinito
        getContext().getLog().info("Waiting for input from NodeRed");
        String line = in.readLine();
        getContext().getLog().info("Successfully received output from NodeRed: {}", line);


        if (line.equals("END")){
            getContext().getLog().info("Finished 1 computation");
            getContext().getSelf().tell(new Update(0, "START"));
            return this;
        }

        //Convert from JSON to Object
        //ObjectMapper JSONtoObject = new ObjectMapper();
        //Object object = JSONtoObject.readValue(line, Object.class);


        int nextStep = event.step + 1;

        if (!runningInstallations.isEmpty()) {
            Random generator = new Random();
            ActorRef<NodeRedInstallation.ReceiveInput> selectedNodeRed = runningInstallations.get(
                    generator.nextInt(runningInstallations.size()));

            Duration timeout = Duration.ofSeconds(5);
            getContext().ask(
                    NodeRedInstallation.ReturnOutput.class,
                    selectedNodeRed,
                    timeout,
                    responseRef -> new NodeRedInstallation.ReceiveInput("UPDATE", nextStep, line, responseRef),
                    (response, failure) -> {
                        if (response != null) {
                            return new UpdateSuccessful();
                        } else {
                            return new UpdateFailed();
                        }
                    });
        }
        return this;
    }
}