package sample.cluster.middlew;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import java.time.Duration;
import java.util.*;


public class RepositoryAdmin extends AbstractBehavior<RepositoryAdmin.Event> {

    interface Event{}

    private static final class NRInstallationsUpdated implements Event {
        public final Set<ActorRef<NodeRedInstallation.ReceiveInput>> newInstallations;

        public NRInstallationsUpdated(Set<ActorRef<NodeRedInstallation.ReceiveInput>> newInstallations) {
            this.newInstallations = newInstallations;
        }
    }

    public static final class StartCompleted implements Event{}
    public static final class StartFailed implements Event{}


    public RepositoryAdmin(ActorContext<Event> context) {
        super(context);

        // We tell to the receptionist that we are interested in update on the list of NR installations.
        // It is like subscribe to a topic
        ActorRef<Receptionist.Listing> subscriptionAdapter =
                context.messageAdapter(Receptionist.Listing.class, listing ->
                        new NRInstallationsUpdated(listing.getServiceInstances(NodeRedInstallation.NODERED_SERVICE_KEY)));

        context.getSystem().receptionist().tell(Receptionist.subscribe(NodeRedInstallation.NODERED_SERVICE_KEY, subscriptionAdapter));
    }

    public static Behavior<Event> create() {
        return Behaviors.setup(RepositoryAdmin::new);
    }


    // This list represent the repository
    private final List<ActorRef<NodeRedInstallation.ReceiveInput>> runningInstallations = new ArrayList<>();
    private final static int THRESHOLD_FOR_START = 1;
    private boolean started = false;

    @Override
    public Receive<Event> createReceive() {
        return newReceiveBuilder()
                .onMessage(NRInstallationsUpdated.class, this::onInstallationUpdated)
                .build();
    }

    private Behavior<Event> onInstallationUpdated(NRInstallationsUpdated update) {
        // Get all the NR installation registered to the receptionist till now;
        // In this way if this actor crash, when a new one is instantiated he get the up-to-date
        // ist of NR installations from the Receptionist
        if (runningInstallations.size() > update.newInstallations.size()){
            getContext().getLog().info("A running installation has just left\n");
        }
        else if (runningInstallations.size() < update.newInstallations.size()){
            getContext().getLog().info("A new installation has joined!\n");
        }

        runningInstallations.clear();
        runningInstallations.addAll(update.newInstallations);
        if (!runningInstallations.isEmpty()) {
            getContext().getLog().info("List of currently running installations:");
            for (ActorRef<NodeRedInstallation.ReceiveInput> inst: runningInstallations) {
                getContext().getLog().info("Installation: {}", inst);
            }
        }
        else{
            getContext().getLog().info("No installation is running");
            started = false;
        }

        // If the current set of running installation is bigger than a threshold, Start the computation
        if (runningInstallations.size() >= THRESHOLD_FOR_START && !started){
            this.OnStartCompleted();
            started = true;
        }
        return this;
    }

    private void OnStartCompleted() {
            // Select randomly a NodeRedInstallation for the computation of next step
            Random generator = new Random();
            ActorRef<NodeRedInstallation.ReceiveInput> selectedNodeRed = runningInstallations.get(
                                                                generator.nextInt(runningInstallations.size()));
            // how much time can pass before we consider a request failed
            Duration timeout = Duration.ofSeconds(5);
            getContext().getLog().info("Starting computation on installation {}", selectedNodeRed);
            getContext().ask(
                    NodeRedInstallation.ReturnOutput.class,
                    selectedNodeRed,
                    timeout,
                    responseRef -> new NodeRedInstallation.ReceiveInput("UPDATE", 0, "START", responseRef),
                    (response, failure) -> {
                        if (response != null) {
                            return new StartCompleted();
                        } else {
                            getContext().getLog().info("Error in Computation Step in {}", selectedNodeRed);
                            return new StartFailed();
                        }
                    });
    }
}
