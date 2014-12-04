package gpsweb.parser;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author furbina
 */
public class TestTaipParserWorker {

	TaipParserWorker worker;
	Connection conexionTsensorFinal;
	Connection dataBaseConnection;
	private static final String USER_TSENSOR_FINAL = "tsonline";
	private static final String PASS_TSENSOR_FINAL = "tsensor";
	private static final String DATABASE_TSENSOR_FINAL = "jdbc:mysql://190.54.34.35/tsensor_final";
	private static final String USER_GPS = "gpsweb";
	private static final String PASS_GPS = "gpsweb";
	private static final String DATABASE_GPS = "jdbc:postgresql://190.54.34.35:5432/gpsweb";

	@Before
	public void init() {
		worker = new TaipParserWorker(10, 10, 10);
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			conexionTsensorFinal = DriverManager.getConnection(DATABASE_TSENSOR_FINAL, USER_TSENSOR_FINAL,
					PASS_TSENSOR_FINAL);
			// out.println("Conectado a base de datos: " +conexion);
			dataBaseConnection = DriverManager.getConnection(DATABASE_GPS, USER_GPS, PASS_GPS);
			
		} catch (Exception e) {
			Log.getLogger().error(e);
			Logger.getLogger(TestTaipParserWorker.class.getName()).log(Level.SEVERE, null, e);
		}
	}

	@Test
	public void testCheckSensor() {
		String codigoSensor = "W01TS9999";
		String codigoSensorNuevo = "W10TS9999";

		assertTrue(worker.checkSensor(codigoSensor));
		assertFalse(worker.checkSensor(codigoSensorNuevo));

		try {
			if (conexionTsensorFinal.isClosed())
				conexionTsensorFinal = DriverManager.getConnection(DATABASE_TSENSOR_FINAL, USER_TSENSOR_FINAL,
						PASS_TSENSOR_FINAL);
		} catch (SQLException e1) {
			Log.getLogger().error(e1.getMessage());
		}
		String sql = "DELETE FROM sensores WHERE codigo='" + codigoSensorNuevo + "'";
		Statement st;
		try {
			synchronized (this) {
				st = conexionTsensorFinal.createStatement();
				st.execute(sql);
			}
			st.close();
		} catch (SQLException e) {
			Log.getLogger().error(e);
		}
	}

	@Test
	public void testCheckSimcard() {
		String codigoSimcard = "TS9999";
		String codigoSimcardNuevo = "TS8888";

		assertTrue(worker.checkSimcard(codigoSimcard));
		assertFalse(worker.checkSimcard(codigoSimcardNuevo));

		try {
			if (conexionTsensorFinal.isClosed())
				conexionTsensorFinal = DriverManager.getConnection(DATABASE_TSENSOR_FINAL, USER_TSENSOR_FINAL,
						PASS_TSENSOR_FINAL);
		} catch (SQLException e1) {
			Log.getLogger().error(e1.getMessage());
		}
		String sql = "DELETE FROM simcards WHERE simcard='" + codigoSimcardNuevo + "'";
		Statement st;
		try {
			synchronized (this) {
				st = conexionTsensorFinal.createStatement();
				st.execute(sql);
			}
			st.close();
		} catch (SQLException e) {
			Log.getLogger().error(e);
		}
	}

	@Test
	public void testGetTimeZoneByCompanyId () {
		//Olimex -> timezone = -3
		String companyId = "3";
		int timezone = worker.getTimeZoneByCompanyId(companyId, dataBaseConnection);
		assertEquals(-3, timezone);
	}

	@Test
	public void testSaveTemperature () {
		//saveTemperature(String simcard, String sensor, String temp, String date, String companyId) {
		String simcard = "TS9999";
		String sensor = "W01TS9999";
		String temp = "10";
		String date = "2011-11-14 13:44:00";
		String companyId = "3";
		assertTrue(worker.saveTemperature(simcard, sensor, temp, date, companyId, dataBaseConnection));
	}
}
