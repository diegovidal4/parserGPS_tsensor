/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gpsweb.parser;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 *
 * @author dbl
 */
public class TestProperties {


    public static void main (String ars[]){

        System.out.println(" SS "  + System.getProperty("service.conf"));
        
        Properties properties = new Properties();
        properties.setProperty("LOG_DIR", "/home/gps/tmp/");

        try { properties.store(new FileOutputStream("/tmp/ss.properties"), null); } catch (IOException e) { }

    }


}
