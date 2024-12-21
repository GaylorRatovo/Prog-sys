import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProxyThread implements Runnable {
    private final Socket clientSocket;
    private final String apacheHost;
    private final int apachePort;

    // Cache en mémoire partagée entre les threads
    private static final Map<String, byte[]> cache = new ConcurrentHashMap<>();
    private static final Map<Integer, String> num_cache = new ConcurrentHashMap<>();

    private static int count = 0;
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ProxyThread(Socket clientSocket, String apacheHost, int apachePort) {
        this.clientSocket = clientSocket;
        this.apacheHost = apacheHost;
        this.apachePort = apachePort;
    }

    public static Map<String, byte[]> getCache() {
        return cache;
    }

    public static Map<Integer, String> getNumCache() {
        return num_cache;
    }

    @Override
    public void run() {
        try (InputStream clientInput = clientSocket.getInputStream();
             OutputStream clientOutput = clientSocket.getOutputStream()) {

            // Lire la première ligne de la requête du client
            BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientInput));
            String requestLine = clientReader.readLine(); // Lire la première ligne
            System.out.println("Requête reçue du client : " + requestLine);

            // Vérifier si la réponse est dans le cache
            if (cache.containsKey(requestLine)) {
                System.out.println("Réponse trouvée dans le cache !");

                // Envoyer directement la réponse en binaire
                clientOutput.write(cache.get(requestLine));
                clientOutput.flush();
                return; // Fin du traitement, réponse déjà envoyée
            }

            // Si pas dans le cache, relayer la requête vers Apache
            try (Socket apacheSocket = new Socket(apacheHost, apachePort);
                 OutputStream apacheOutput = apacheSocket.getOutputStream();
                 InputStream apacheInput = apacheSocket.getInputStream()) {

                PrintWriter apacheWriter = new PrintWriter(apacheOutput, true);
                apacheWriter.println(requestLine);

                // Transférer les en-têtes restants
                String line;
                while ((line = clientReader.readLine()) != null && !line.isEmpty()) {
                    apacheWriter.println(line);
                }
                apacheWriter.println(); // Terminer les en-têtes HTTP

                // Relayer la réponse d'Apache au client
                ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; // Taille du buffer
                int bytesRead;

                // Lire la réponse d'Apache en binaire
                while ((bytesRead = apacheInput.read(buffer)) != -1) {
                    responseBuffer.write(buffer, 0, bytesRead);
                }

                // Stocker la r&eacute;ponse dans le cache si elle est de type texte
                cache.put(requestLine, responseBuffer.toByteArray());
                num_cache.put(count, requestLine);

                // Supprimer la réponse du cache après un délai
                handleCacheExpiration(requestLine, responseBuffer.size());

                count++;

                // Transmettre la réponse au client
                clientOutput.write(responseBuffer.toByteArray());
                clientOutput.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleCacheExpiration(String requestLine, int responseSize) {
        int delai = 10; // Par défaut
        try {
            Properties properties = new Properties();
            try (FileInputStream fis = new FileInputStream("config/config.properties")) {
                properties.load(fis);
            }
            delai = Integer.parseInt(properties.getProperty("default-delay", "10"));
        } catch (IOException e) {
            System.out.println("Erreur lors du chargement du fichier de configuration : " + e.getMessage());
        }

        if (responseSize >= 1000000 && responseSize < 2000000) {
            delai = 30;
        } else if (responseSize >= 2000000) {
            delai = 60;
        }

        ProxyServer.removeFromCache(requestLine, count, delai);
    }

}
