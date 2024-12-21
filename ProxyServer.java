import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProxyServer {
    private static volatile boolean running = true; // Indicateur pour arrêter le serveur
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    public static void removeFromCache(String r,int c,int duration){
        scheduler.schedule(()->{
            ProxyThread.getCache().remove(r);
            System.out.println("Pourquoi t'as deco de la playstation, il faut pas rager il faut pas rage quitter");
            ProxyThread.getNumCache().remove(c);
        },duration, TimeUnit.MINUTES);
    }

    public static void main(String[] args) {
        // Charger les paramètres depuis le fichier config.properties
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("config/config.properties")) {
            properties.load(fis);
        } catch (IOException e) {
            System.out.println("Erreur lors du chargement du fichier de configuration : " + e.getMessage());
            return;
        }

        // Lire les paramètres
        int proxyPort = Integer.parseInt(properties.getProperty("proxyPort"));
        String apacheHost = properties.getProperty("apacheHost");
        int apachePort = Integer.parseInt(properties.getProperty("apachePort"));

        System.out.println("Configuration chargée :");
        System.out.println("Proxy Port: " + proxyPort);
        System.out.println("Apache Host: " + apacheHost);
        System.out.println("Apache Port: " + apachePort);

        Thread commandThread = new Thread(() -> {
            try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
                String command;

                while (true) {
                    System.out.print("> ");
                    command = consoleReader.readLine();

                    if ("stop".equalsIgnoreCase(command)) {
                        running = false; // Arrêter le serveur
                        System.out.println("Commande d'arrêt reçue. Arrêt du serveur...");
                        break; // Quitter la boucle
                    }

                    if ("clear-cache".equalsIgnoreCase(command)) {
                        ProxyThread.getCache().clear();
                        ProxyThread.getNumCache().clear();
                        System.out.println("Cache vidé ...");
                    } else if ("show-cache".equalsIgnoreCase(command)) {
                        // Afficher le contenu du cache
                        System.out.println("Contenu du cache :");

                        Map<Integer, String> caches = ProxyThread.getNumCache();
                        caches.entrySet().stream().map(cache -> "Num : " + cache.getKey() + " => " + cache.getValue()).forEach(System.out::println);

                    } else if (command.startsWith("remove-cache ")) {
                        // Supprimer une entrée du cache
                        int num = Integer.parseInt(command.substring("remove-cache ".length()).trim());
                        String keyToRemove = ProxyThread.getNumCache().get(num);

                        if (ProxyThread.getCache().remove(keyToRemove) != null) {
                            ProxyThread.getNumCache().remove(num);
                            System.out.println("Clé '" + keyToRemove + "' supprimée du cache.");
                        } else
                            System.out.println("Clé '" + keyToRemove + "' introuvable dans le cache.");

                    } else {
                        System.out.println("Commande inconnue. Commandes disponibles : stop, show-cache, remove-cache <clé>, clear-cache");
                    }
                }
            } catch (IOException e) {
                System.err.println("Erreur lors de la lecture des commandes : " + e.getMessage());
            }
        });
        commandThread.start();

        // Démarrer le serveur proxy
        try (ServerSocket serverSocket = new ServerSocket(proxyPort)) {
            System.out.println("Proxy Java en écoute sur le port " + proxyPort);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Un client accepté");

                    new Thread(new ProxyThread(clientSocket, apacheHost, apachePort)).start();
                } catch (SocketException e) {
                    if (!running) {
                        System.out.println("Le serveur a été arrêté proprement.");
                        break;
                    }
                    throw e; // Ré-échouer si c'est une autre erreur
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Attendre la fin du thread de commande
        try {
            commandThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Le thread de commande a été interrompu.");
        }

        System.out.println("Serveur arrêté.");
    }
}
