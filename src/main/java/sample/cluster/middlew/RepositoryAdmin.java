package sample.cluster.middlew;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import java.util.*;


public class RepositoryAdmin extends AbstractBehavior<RepositoryAdmin.Event> {

    interface Event{}

    /**
     * Messages(events) that the actor can handle
     **/
    private static final class NRInstallationsUpdated implements Event {
        public final Set<ActorRef<NodeRedInstallation.ReceiveInput>> newInstallations;

        public NRInstallationsUpdated(Set<ActorRef<NodeRedInstallation.ReceiveInput>> newInstallations) {
            this.newInstallations = newInstallations;
        }
    }


    /**
     * Constructor
     **/
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


    /**
     * Actor State and variables
     **/
    // This list represent the repository
    private final List<ActorRef<NodeRedInstallation.ReceiveInput>> runningInstallations = new ArrayList<>();

    @Override
    public Receive<Event> createReceive() {
        return newReceiveBuilder()
                .onMessage(NRInstallationsUpdated.class, this::onInstallationUpdated)
                .build();
    }


    /**
     * Behaviors of the actor
     **/
    private Behavior<Event> onInstallationUpdated(NRInstallationsUpdated update) {
        // Get all the NR installation registered to the receptionist till now,
        // querying the local Receptionist replicator
        if (runningInstallations.size() > update.newInstallations.size()){
            getContext().getLog().info("A running installation has just left\n");
        }
        else if (runningInstallations.size() < update.newInstallations.size()){
            getContext().getLog().info("A new installation has joined!\n");
        }

        // Print info about running installations
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
        }

        return this;
    }
}
