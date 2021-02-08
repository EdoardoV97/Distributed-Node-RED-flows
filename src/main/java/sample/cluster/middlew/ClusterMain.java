package sample.cluster.middlew;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.typed.Cluster;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ClusterMain {

  // Socket port to communicate on localhost with NodeRed
  //private static int PORTIN, PORTOUT;

  private static class RootBehavior {

    static Behavior<Void> create() {
      return Behaviors.setup(context -> {
        Cluster cluster = Cluster.get(context.getSystem());

        // Create a new actor associated to the NodeRedInstallation
        if (cluster.selfMember().hasRole("NodeRedInstallation")) {
            context.spawn(NodeRedInstallation.create(), "NodeRedInstallation");
        }
        // Spawn the RepAdmin
        if (cluster.selfMember().hasRole("Server")) {
          context.spawn(RepositoryAdmin.create(), "RepositoryAdmin");
        }

        // This is empty because the Root actor does not need to implement any custom behaviour
        return Behaviors.empty();
      });
    }
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      //startup("Server", 25251);
      startup("NodeRedInstallation", 0);
    } else {
      if (args.length != 2)
        throw new IllegalArgumentException("Usage: role port");
      //PORTIN =
      startup(args[0], Integer.parseInt(args[1]));
    }
  }


  private static void startup(String role, int port) {
    // Override the configuration of the port
    Map<String, Object> overrides = new HashMap<>();
    overrides.put("akka.remote.artery.canonical.port", port);
    overrides.put("akka.cluster.roles", Collections.singletonList(role));

    Config config = ConfigFactory.parseMap(overrides)
        .withFallback(ConfigFactory.load("application"));

    //ActorSystem<Void> system = ActorSystem.create(RootBehavior.create(), "ClusterSystem", config);
    ActorSystem.create(RootBehavior.create(), "ClusterSystem", config);
  }
}
