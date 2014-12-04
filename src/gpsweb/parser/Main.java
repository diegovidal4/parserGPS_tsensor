package gpsweb.parser;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

import org.apache.log4j.PropertyConfigurator;
import org.postgresql.ds.PGPoolingDataSource;

public class Main {

    static PGPoolingDataSource source = null;
    static final String pattern = "[%p] %d - %c - %t - %m%n";
    private static Properties props = new Properties();

    static Connection getConnection() throws SQLException {

        return source.getConnection();
    }

    private static void getProperities() {

        InputStream is = null;
        try {
            is = new FileInputStream(System.getProperty("service.conf"));
            props.load(is);

        } catch (NullPointerException ne) {
            System.err.println("Prop service.conf is : " + System.getProperty("service.conf") + ne.getLocalizedMessage());
            System.exit(-1);
        } catch (IOException ioe) {
            System.err.println("Couldnt find or reading properties file : " + ioe.getLocalizedMessage());
            System.exit(-1);
        }

    }

    /*
     * Carga programaticamente las propiedades de logging
     * Define formatos, y rutas de los archivos de log
     * y la periodicidad de la rotacion de archivos.
     *
     */
    private static void setupLog4J() {

        Properties p = new Properties();
        p.setProperty("log4j.rootLogger", "ALL,root");
        p.setProperty("log4j.appender.root.DatePattern", "'.'yyyy-MM-dd");
        p.setProperty("log4j.appender.root", "org.apache.log4j.DailyRollingFileAppender");
        p.setProperty("log4j.appender.root.layout", "org.apache.log4j.PatternLayout");
        p.setProperty("log4j.appender.root.file", props.getProperty("LOG_DIR") + "/" + props.getProperty("SERVICE_TAG") + ".log");
        p.setProperty("log4j.appender.root.layout.ConversionPattern", pattern);
        PropertyConfigurator.configure(p);

    }

    /*
     * Muestra las propiedades definidas en el archivod de conf
     * en el log.
     */
    public static void showConfiguration() {
        Log.getLogger().info("======SERVICE CONFIGURATION======");
        Enumeration<Object> keys = props.keys();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            String value = (String) props.get(key);
            Log.getLogger().info(key + ": " + value);
        }
        Log.getLogger().info("======END CONFIGURATION======");
    }

    /*
     * Inicia el pool de conexiones a la Base de datos
     * los parametros vienen en archivo de conf del servicio.
     */
    private static void setupDatabase() {

        source = new PGPoolingDataSource();
        source.setDataSourceName(props.getProperty("DB_NAME"));
        source.setServerName(props.getProperty("DB_HOST"));
        source.setDatabaseName(props.getProperty("DB_NAME"));
        source.setUser(props.getProperty("DB_USER"));
        source.setPortNumber(Integer.parseInt(props.getProperty("DB_PORT")));
        source.setPassword(props.getProperty("DB_PASSWORD"));
        source.setMaxConnections(10);
    }

    public static void main(String[] args) {
        getProperities();
        setupLog4J();
        setupDatabase();
        showConfiguration();


        int frameCount = 75;

        try {
            frameCount = Integer.parseInt(props.getProperty("FRAME_COUNT"));
        } catch (Exception e) {
            Log.getLogger().info("Setting frame count to 50 ", e);
            frameCount = 50;
        }

        int thread_num = 1;


        try {
            thread_num = Integer.parseInt(props.getProperty("WORKER_NUMBER"));
        } catch (Exception e) {
            Log.getLogger().info("Setting worker numer to 50 ", e);
            thread_num = 1;
        }

        Thread parsers[] = new Thread[thread_num];

        for (int i = 0; i < thread_num; i++) {
            TaipParserWorker worker = new TaipParserWorker(frameCount, i, thread_num);
            parsers[i] = new Thread(worker, "Taip-Parser " + i);
            parsers[i].start();
        }
        try {

            for (int i = 0; i < thread_num; i++) {
                parsers[i].join();
            }
        } catch (InterruptedException ex) {
            Log.getLogger().fatal("Execpcion partiendo parser , saliendo", ex);
            System.exit(-1);
        }
    }

    public static void mainParsingTest(String[] args) {

        String frame = ">REV011617286058-3343513-0706019800215111;BL=3717;SV=00;ID=353781013834179";
        long now = System.currentTimeMillis();
        HashMap<String, Integer> allowed = new HashMap<String, Integer>();
        allowed.put("RTEV01", 1);

        for (int i = 0; i < 1; i++) {
            GpsEvent e = TaipParser.parseTaip(frame);
            System.out.println(e.gpsDate + " -- " + e.eventid);
        }
        System.out.println(String.format(" time  %d ", (System.currentTimeMillis() - now)));
    }
}
