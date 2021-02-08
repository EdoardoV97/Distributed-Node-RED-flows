package sample.cluster.middlew;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
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

    private static final class NRInstallationsUpdated implements Event {
        public final Set<ActorRef<NodeRedInstallation.ReceiveInput>> newInstallations;

        public NRInstallationsUpdated(Set<ActorRef<NodeRedInstallation.ReceiveInput>> newInstallations) {
            this.newInstallations = newInstallations;
        }
    }

    public static final class Update implements Event {
        public final int step;
        public final Object result;

        public Update(int step, Object result) {
            this.step = step;
            this.result = result;
        }
    }

    public static final class UpdateSuccessful implements Event{}
    public static final class UpdateFailed implements Event{}

    public NodeRedBroker(ActorContext<Event> context) {
        super(context);
        ActorRef<Receptionist.Listing> subscriptionAdapter =
                context.messageAdapter(Receptionist.Listing.class, listing ->
                        new NodeRedBroker.NRInstallationsUpdated(listing.getServiceInstances(NodeRedInstallation.NODERED_SERVICE_KEY)));

        context.getSystem().receptionist().tell(Receptionist.subscribe(NodeRedInstallation.NODERED_SERVICE_KEY, subscriptionAdapter));

        nodeRedSocketOUT = new ServerSocketToNodeRed(PORTOUT);
        nodeRedSocketIN = new ServerSocketToNodeRed(PORTIN);
    }

    public static Behavior<NodeRedBroker.Event> create() {
        return Behaviors.setup(NodeRedBroker::new);
    }


    /** Actor State and variables **/
    // This list represent the repository
    private final List<ActorRef<NodeRedInstallation.ReceiveInput>> runningInstallations = new ArrayList<>();
    int PORTOUT = 12345; // To send input to NodeRed
    int PORTIN = 56789; // To receive output from NodeRed
    ServerSocketToNodeRed nodeRedSocketOUT;
    ServerSocketToNodeRed nodeRedSocketIN;


    @Override
    public Receive<NodeRedBroker.Event> createReceive() {
        return newReceiveBuilder()
                .onMessage(NodeRedBroker.NRInstallationsUpdated.class, this::onInstallationUpdated)
                .onMessage(NodeRedBroker.Update.class, this::onUpdateReceived)
                .build();
    }


    private Behavior<NodeRedBroker.Event> onInstallationUpdated(NodeRedBroker.NRInstallationsUpdated update) {
        runningInstallations.clear();
        runningInstallations.addAll(update.newInstallations);
        return this;
    }


    private Behavior<Event> onUpdateReceived (NodeRedBroker.Update event) throws IOException {

        int increment = 1;

        ObjectOutputStream out = nodeRedSocketOUT.getOut();
        BufferedReader in = nodeRedSocketIN.getIn();

        // Convert Object to JSON to send to NodeRed
        SocketObject socketObject = new SocketObject(event.step, event.result);
        ObjectMapper ObjectToJSON = new ObjectMapper();
        String json;
        json = ObjectToJSON.writeValueAsString(socketObject);


        // Send the input to NodeRed
        out.writeObject(json);
        out.flush();
        getContext().getLog().info("Successfully send input to NodeRed");


        //Receive the output from NodeRed
        //TODO aggiungere Thread o attore per leggere input all'infinito
        getContext().getLog().info("Waiting for input from NodeRed");
        String line = in.readLine();
        getContext().getLog().info("Received: {}", line);


        if (line.equals("END")){
            getContext().getLog().info("Finished 1 computation");
            increment = event.step;

            //TODO non Chiudere le socket se si vuole continuare con altre computazioni
            nodeRedSocketIN.stop();
            nodeRedSocketOUT.stop();
            //return this;
        }

        //Convert from JSON to NodeRed
        ObjectMapper JSONtoObject = new ObjectMapper();
        Object object = JSONtoObject.readValue(line, Object.class);

        int nextStep = event.step + increment;

        if (!runningInstallations.isEmpty()) {
            Random generator = new Random();
            ActorRef<NodeRedInstallation.ReceiveInput> selectedNodeRed = runningInstallations.get(
                    generator.nextInt(runningInstallations.size()));

            Duration timeout = Duration.ofSeconds(5);
            getContext().ask(
                    NodeRedInstallation.ReturnOutput.class,
                    selectedNodeRed,
                    timeout,
                    responseRef -> new NodeRedInstallation.ReceiveInput("UPDATE", nextStep, object, responseRef),
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