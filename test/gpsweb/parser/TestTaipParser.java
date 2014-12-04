/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gpsweb.parser;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author vfreire
 */
public class TestTaipParser {
	
	@Test
	public void TestParseTaip() {
		
		String frame= ">RTXEV011672684774-3350283-0706698100000012;T1_ID=0000032ECC58;T1_TMP=25.0706703700000012;T1_ID=;ID=357273031357645";
		
        GpsEvent evento = TaipParser.parseTaip(frame);
		
		assertEquals(evento.status, parseStatus.BAD_FORMAT);
		
    }
	
}
