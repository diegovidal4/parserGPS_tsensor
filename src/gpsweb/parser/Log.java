package gpsweb.parser;

import org.apache.log4j.Logger;

public class Log {

    private static Logger logger = Logger.getLogger(Main.class);

    static public Logger getLogger() {
        return logger;
    }
}
