package gpsweb.parser;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.log4j.Logger;

/* Clase para procesar colas de eventos en BD */
public class TaipParserWorker implements Runnable {

	private static Logger logger = Logger.getLogger(TaipParserWorker.class);
	
	private String getFramesSql = null;
	private HashMap<String, MobileLigth> mobiles = new HashMap<String, MobileLigth>();
	private long lastMobileConf = 0L;
	private long lastPrintMsg = 0L;
	/* Periodo para refrescar lista de mobiles asociados*/
	final static private long periodMobileConf = 1000 * 60 * 5L;
	/* Consulta para insertar un nuevo evento a la BD,calculando la geocerca inmediatamente */
	private static final String STORE_EVENT = "INSERT INTO \"GpsEvent\" (latitude, longitude, speed,direction, date, \"type\", \"mobileId\",\"signalStrength\",id,\"temperatureId\",geofence_id) VALUES (?, ?, ?, ?, cast(? AS timestamp without time zone), ?, ?, ?, ?, ?,(select id from geofence where company_id = ? AND geopoly @> point (?,?)  LIMIT 1))";
	/* Consulta para actualizar evento en la base de datos de tramas TAIP */
	private static final String STORE_FRAME_PARSED = "update gpsrecenttaip set parsedresult = ? where id = ? ";
	/* Consulta para almacenar temperaturas */
	private static final String ADD_TEMPERATURE = "INSERT INTO \"Temperature\" (temp1,temp2,temp3,temp4,temp5,id) VALUES (?,?,?,?,?,?)";
	/* Id de cola de eventos a procesar */
	private int threadIndex = 0;
	/* Numero total de threads que parsean */
	static private int threadNum = 0;
	/**
	 * Conexion a base de datos tsensor_final (MySQL Actualmente)
	 */
	protected Connection conexionTsensorFinal;
	/**
	 * Nombre de usuario
	 */
	private static final String USER_TSENSOR_FINAL = "root";
//	private static final String USER_TSENSOR_FINAL = "tsonline";
	/**
	 * Password
	 */
	private static final String PASS_TSENSOR_FINAL = "tsensor";
//	private static final String PASS_TSENSOR_FINAL = "tsensor";
	/**
	 * Nombre de base de datos
	 */
	private static final String DATABASE_TSENSOR_FINAL = "jdbc:mysql://localhost/tsensor_final";
//	private static final String DATABASE_TSENSOR_FINAL = "jdbc:mysql://190.54.34.35/tsensor_final";

		/**
	 * Nombre de usuario
	 */
//	private static final String USER_GPS = "gpsweb";
	/**
	 * Password
	 */
//	private static final String PASS_GPS = "gpsweb";
	/**
	 * Nombre de base de datos
	 */
//	private static final String DATABASE_GPS = "jdbc:postgresql://190.54.34.35:5432/gpsweb";

	public TaipParserWorker(int limitRead, int _threadIndex, int _thread_num) {
		threadIndex = _threadIndex;
		threadNum = _thread_num;
		getFramesSql = String.format("SELECT frame,time,id FROM gpsrecenttaip WHERE parsedresult = '0' AND (id %% %d)=%d ORDER BY id ASC limit %d", threadNum, threadIndex, limitRead);
		logger.info("getFramesSql : " + getFramesSql);
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			conexionTsensorFinal = DriverManager.getConnection(DATABASE_TSENSOR_FINAL, USER_TSENSOR_FINAL,
					PASS_TSENSOR_FINAL);

		} catch (Exception e) {
			logger.error(e);
		}

	}

	public void run() {
		int proc = 0;
		Connection connection = null;
		try {
			connection = Main.getConnection();
			while (true) {
				proc = doWork(connection);
				if (proc == 0) {
					long now = System.currentTimeMillis();
					if (now - lastPrintMsg > periodMobileConf * 10) {
						logger.debug(String.format("[%s] Sleep Thread : %s ", TaipParser.sdf.format(new java.util.Date()), Thread.currentThread().getName()));
						lastPrintMsg = now;
					}
					Thread.sleep(5000L);
				} else {
					Thread.sleep(10000L);
				}
			}
		} catch (SQLException ex) {

			logger.error("Excepcion SQL ", ex);
		} catch (InterruptedException ex) {
			logger.error("Excepion Sleep ", ex);
		}

	}

	int doWork(Connection connection) throws SQLException {

		int qsize = 0;
		ArrayList<GpsEvent> currentGpsEventQueue = new ArrayList<GpsEvent>();

		PreparedStatement retrieveFrameStmt = null;
		ResultSet frameRows = null;
		PreparedStatement framestmt = null;
		Long now = System.currentTimeMillis();
		GpsEvent event = null;
		PreparedStatement addEventsmt = null;
		PreparedStatement addTemperaturestmt = null;

		MobileLigth mobile = null;

		try {
			retrieveFrameStmt = connection.prepareStatement(getFramesSql);
			frameRows = retrieveFrameStmt.executeQuery();
			HashMap<Long, String> frames = new HashMap<Long, String>();

			while (frameRows.next()) {
				frames.put(frameRows.getLong("id"), frameRows.getString("frame"));
			}

			closeResultSet(frameRows);
			closePrepareStatement(retrieveFrameStmt);
			/* actualizar lista de mobiles si corresponde */
			updateMobileList(mobiles, connection);
			framestmt = connection.prepareStatement(STORE_FRAME_PARSED);
			Iterator it = frames.entrySet().iterator();
			connection.setAutoCommit(false);

			int ii = 0;
			while (it.hasNext()) {
				Map.Entry<Long, String> theFrame = (Map.Entry<Long, String>) it.next();
				event = TaipParser.parseTaip(theFrame.getValue());
				
				/* rescue mobile from list */
				mobile = mobiles.get(event.deviceID);
				if (mobile == null) {
					event.status = parseStatus.UNKNOWN_MOBILE;
				} else {
					event.movilId = mobile.getmobileId();
					event.companyId = mobile.companyId;
				}

				event.frameId = theFrame.getKey();
				/* Evento parseado OK*/
				if (event.status == parseStatus.OK) {
					currentGpsEventQueue.add(ii, event);
					ii++;
				} else {
					/* Ante cualquier error dar por descartado la trama taip */
					framestmt.setInt(1, event.status.getCode());
					framestmt.setLong(2, theFrame.getKey());
					framestmt.addBatch();
					logger.debug(String.format("[ par_result : %d, thread : %d, idFrame = %s, parseStatus = %s]", event.status.getCode(), threadIndex, theFrame.getKey(), event.status));

				}
			}
			framestmt.executeBatch();
			connection.commit();

			closePrepareStatement(framestmt);

			/* Almacenar eventos en la tabla "GpsEvent*/

			qsize = currentGpsEventQueue.size();
			if (qsize > 0) {
				logger.info(String.format("Processing : %d events on QUEUE : %d", currentGpsEventQueue.size(), threadIndex));

				framestmt = connection.prepareStatement(STORE_FRAME_PARSED);
				addEventsmt = connection.prepareStatement(STORE_EVENT);
				addTemperaturestmt = connection.prepareStatement(ADD_TEMPERATURE);

				for (GpsEvent theEvent : currentGpsEventQueue) {
					addEventsmt.setFloat(1, theEvent.latitude);
					addEventsmt.setFloat(2, theEvent.longitude);
					addEventsmt.setFloat(3, theEvent.speed);
					addEventsmt.setFloat(4, theEvent.direction);
					addEventsmt.setString(5, theEvent.gpsDate);
					addEventsmt.setString(6, theEvent.eventid);
					addEventsmt.setInt(7, theEvent.movilId);
					addEventsmt.setFloat(8, theEvent.signalStrength);
					addEventsmt.setLong(9, theEvent.frameId);
					addEventsmt.setInt(11, theEvent.companyId);
					addEventsmt.setFloat(12, theEvent.latitude);
					addEventsmt.setFloat(13, theEvent.longitude);

					//********************* guarda a una tabla con solo eventos de apertura puertas y exceso velocidad******************
					
					if ("SPEEDING".equals(theEvent.eventid) || "ENTRY1_ACTIVE".equals(theEvent.eventid) || "ENTRY1_INACTIVE".equals(theEvent.eventid)|| "ENTRY2_ACTIVE".equals(theEvent.eventid)
							|| "ENTRY2_INACTIVE".equals(theEvent.eventid)|| "ENTRY3_ACTIVE".equals(theEvent.eventid)|| "ENTRY3_INACTIVE".equals(theEvent.eventid))
					{
//					     logger.info(theEvent.eventid+" - "+theEvent.deviceID+" - "+theEvent.latitude+" - "+theEvent.longitude+" - "+theEvent.speed+" - "+theEvent.companyId+" Se almacen'o correctamente El evento puertas");
						if (saveEvento(theEvent.eventid, theEvent.deviceID, theEvent.gpsDate,theEvent.latitude, theEvent.longitude,theEvent.speed, theEvent.companyId+"",connection)) {
										logger.info(" Se almacen'o correctamente El evento puertas");
									} else {
										logger.info("Ocurrio un problema tratando de almacenar el evento");
									}
						
					}
				//****************************************************************************************************
					//add Temperature if it's a frame with temperature


					if ("TEMPERATURE".equals(theEvent.eventid)
							|| "ENTRY1_ACTIVE_TMP".equals(theEvent.eventid)
							|| "ENTRY1_INACTIVE_TMP".equals(theEvent.eventid)
							|| "ENTRY2_ACTIVE_TMP".equals(theEvent.eventid)
							|| "ENTRY2_INACTIVE_TMP".equals(theEvent.eventid)
							|| "ENTRY3_ACTIVE_TMP".equals(theEvent.eventid)
							|| "ENTRY3_INACTIVE_TMP".equals(theEvent.eventid)) {
						addEventsmt.setLong(10, theEvent.frameId);
						String currentTemperature = null;
						Double tmpTemperature = null;
						String sensor = null;

						for (int i = 1; i < 6; i++) {
							currentTemperature = theEvent.optionalParams.get(String.format("T%d_TMP", i));
							if (getTemporalTemperature(currentTemperature) < 85) {
								sensor = theEvent.optionalParams.get(String.format("T%d_ID", i));
								if (sensor != null && theEvent.deviceID != null && currentTemperature != null) {
									// Guardar temperatura
									checkSensor(sensor);
									checkSimcard(theEvent.deviceID);
									if (saveTemperature(theEvent.deviceID, sensor, currentTemperature, theEvent.gpsDate, theEvent.companyId+"", connection)) {
										logger.info("Se almacen'o correctamente la temperatura del sensor " + sensor + " en la DB de telemetr'ia");
									} else {
										logger.info("Ocurri'o un problema tratando de almacenar la temperatura del sensor " + sensor + " en la DB de telemetr'ia");
									}
								}
								if (currentTemperature != null) {
									tmpTemperature = getTemporalTemperature(currentTemperature);

									addTemperaturestmt.setDouble(i, tmpTemperature);
								} else {
									addTemperaturestmt.setNull(i, java.sql.Types.DOUBLE);
								}
							} else {
								addTemperaturestmt.setNull(i, java.sql.Types.DOUBLE);
							}
							logger.info(sensor+" se lee la temp nº"+i);
						}
						logger.info(" FRAME.id " + theEvent.frameId);
						addTemperaturestmt.setLong(6, theEvent.frameId);
						addTemperaturestmt.addBatch();
					} else {
						addEventsmt.setNull(10, java.sql.Types.BIGINT);
					}

					addEventsmt.addBatch();
					framestmt.setInt(1, 1);
					framestmt.setLong(2, theEvent.frameId);
					framestmt.addBatch();
				}

				framestmt.executeBatch();
//                connection.commit();

				addTemperaturestmt.executeBatch();
				//              connection.commit();

				addEventsmt.executeBatch();
				connection.commit();



				logger.info(" currrent Size() " + currentGpsEventQueue.size());
				closePrepareStatement(framestmt);
				closePrepareStatement(addEventsmt);
				closePrepareStatement(addTemperaturestmt);
			}
		} catch (BatchUpdateException e) {
			logger.error("Error insertando datos a BD : ", e);
			logger.error("Next Exception : ", e.getNextException());
			connection.rollback();
		} finally {
			closePrepareStatement(framestmt);
			closePrepareStatement(addEventsmt);
			closePrepareStatement(addTemperaturestmt);
			closePrepareStatement(retrieveFrameStmt);
			closeResultSet(frameRows);
			connection.setAutoCommit(true);
			currentGpsEventQueue.clear();
		}
		if (qsize > 0) {
			logger.info(String.format(" Time processing events  : %d ", (System.currentTimeMillis() - now)));
		}
		return 0;
	}

	/* Obtiene lista de mobiles desde la BD, ver tabla Mobile */
	private void updateMobileList(HashMap<String, MobileLigth> mobileList, Connection dataBaseConnection) throws SQLException {

		long now = System.currentTimeMillis();
		if (now - lastMobileConf > periodMobileConf) {
			logger.debug("Getting mobileList...");
			Statement st = dataBaseConnection.createStatement();
			ResultSet rs = st.executeQuery("select trim(imei) as imei ,id, \"companyId\" as companyid from \"Mobile\" where LENGTH(imei) > 5 ORDER BY id ASC ");
			mobileList.clear();
			while (rs.next()) {
				mobileList.put(rs.getString("imei").trim(), new MobileLigth(rs.getInt("id"), rs.getInt("companyid")));
				logger.debug("IMEI : " + rs.getString("imei") + "  id : " + rs.getInt("id"));
			}
			rs.close();
			st.close();
			lastMobileConf = now;
		}
		return;
	}

	static Double getTemporalTemperature(String tempString) {
		try {
			return Double.parseDouble(tempString);
		} catch (Exception e) {
			return new Double(-1f);
		}
	}

	static void closeResultSet(ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException e) {
			}
		}
		rs = null;
	}

	static void closePrepareStatement(PreparedStatement pst) {
		if (pst != null) {
			try {
				pst.clearBatch();
				pst.clearWarnings();
				pst.close();
			} catch (SQLException e) {
			}
		}
		pst = null;
	}

	/**
	 * Revisa si est'a registrado el c'odigo del sensor que se entrega como parametro, en caso de estar retorna true, en
	 * caso contrario lo agrega a la base de datos y retorna false.
	 * @param sensor C'odigo del sensor
	 * @return true si est'a el sensor en la base de datos, false si no est'a
	 */
	public boolean checkSensor(String sensor) {
		logger.info("Sensor = " + sensor);
		try {
			if (conexionTsensorFinal.isClosed()) {
				conexionTsensorFinal = DriverManager.getConnection(DATABASE_TSENSOR_FINAL, USER_TSENSOR_FINAL,
						PASS_TSENSOR_FINAL);
			}
		} catch (SQLException e1) {
			logger.error(e1.getMessage());
		}
		String sql = "SELECT * FROM sensores WHERE codigo='" + sensor + "'";
		ResultSet rs;
		Statement st;
		try {
			synchronized (this) {
				st = conexionTsensorFinal.createStatement();
				rs = st.executeQuery(sql);
			}
			if (rs.next()) {
				logger.debug("Se encontr'o el sensor " + sensor);
				rs.close();
				st.close();
				return true;
			} else {
				logger.debug("No se encontr'o el sensor " + sensor);
				// Se intenta guardar el codigo del dispositivo en la tabla de los
				// sensores.
				String sqlInsertSensor = "INSERT INTO sensores (codigo, estado, variable_id) ";
				sqlInsertSensor = sqlInsertSensor + "VALUES('" + sensor + "', 6, 1)";

				try {
					synchronized (this) {
						Statement s = conexionTsensorFinal.createStatement();
						s.execute(sqlInsertSensor);
						s.close();
						logger.debug("Se guard'o el sensor " + sensor);
					}
				} catch (NumberFormatException e) {
					logger.error("Error en RTX, codigo del Sensor mal escrito");
				} catch (Exception e) {
					logger.error(e.getMessage());
				}
			}
			try {
				rs.close();
				st.close();
			} catch (SQLException e) {
				logger.error(e.getMessage());
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		return false;
	}

	/**
	 * Revisa si est'a registrado el simcard que se entrega como parametro, en caso de estar retorna true, en
	 * caso contrario lo agrega a la base de datos y retorna false.
	 * @param simcard C'odigo del simcard
	 * @return true si est'a el simcard en la base de datos, false si no est'a
	 */
	public boolean checkSimcard(String simcard) {
		logger.info("Simcard = " + simcard);
		try {
			if (conexionTsensorFinal.isClosed()) {
				conexionTsensorFinal = DriverManager.getConnection(DATABASE_TSENSOR_FINAL, USER_TSENSOR_FINAL,
						PASS_TSENSOR_FINAL);
			}
		} catch (SQLException e1) {
			logger.error(e1.getMessage());
		}
		String sql = "SELECT * FROM simcards WHERE simcard='" + simcard + "'";
		ResultSet rs;
		Statement st;
		try {
			synchronized (this) {
				st = conexionTsensorFinal.createStatement();
				rs = st.executeQuery(sql);
			}
			if (rs.next()) {
				logger.debug("Se encontr'o el simcard " + simcard);
				rs.close();
				st.close();
				return true;
			} else {
				logger.debug("No se encontr'o el simcard " + simcard);
				// Se intenta guardar el codigo del dispositivo en la tabla de los
				// sensores.
				String sqlInsertSimcard = "INSERT INTO simcards (simcard) ";
				sqlInsertSimcard = sqlInsertSimcard + "VALUES('" + simcard + "')";

				try {
					synchronized (this) {
						Statement s = conexionTsensorFinal.createStatement();
						s.execute(sqlInsertSimcard);
						s.close();
						logger.debug("Se guard'o el simcard " + simcard);
					}
				} catch (Exception e) {
					logger.error(e.getMessage());
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
			return false;
		}
		try {
			rs.close();
			st.close();
		} catch (SQLException e) {
			logger.error(e.getMessage());
		}
		return false;
	}
	
public boolean saveEvento(String evento, String simcard, String date,Float lat ,Float lon,Float velocidad, String companyId, Connection connection) {
//		logger.info("Simcard = " + simcard + " Sensor = " + sensor + " temp = " + temp + " date = " + date + " companyId = " + companyId);
		
	long timeZoneInMilliseconds = getTimeZoneByCompanyId(companyId, connection) * 60l * 1000l * 60l;
		Date fecha = stringToDate(date);
		fecha.setTime(fecha.getTime() + timeZoneInMilliseconds);
		java.sql.Timestamp sqlDate = new java.sql.Timestamp(fecha.getTime());
		String latitude= Float.toString(lat);
		String longitude= Float.toString(lon);
		String speed= Float.toString(velocidad);
		if(speed.length()>7){
		speed=speed.substring(0,6);
		}
		
		String event="";
		event=evento;
		// Pruebas del nuevo modulo de alarmas
//		if(evento.equals("ENTRY1_ACTIVE")){  event=evento; }
//		if(evento.equals("ENTRY1_INACTIVE")){  event=evento; }
//		if(evento.equals("ENTRY2_ACTIVE")){  event=evento; }
//		if(evento.equals("ENTRY2_INACTIVE")){  event=evento; }
//		if(evento.equals("ENTRY3_ACTIVE")){  event=evento; }
//		if(evento.equals("ENTRY3_INACTIVE")){  event=evento; }
//		if(evento.equals("VEHICLE_ON")){  event=evento; }
//		if(evento.equals("VEHICLE_OFF")){  event=evento; }
//		if(evento.equals("SPEEDING")){  event=evento; }
		if(evento.trim().equals("")){
		logger.info("evento vacio "+evento);	
		}
		logger.info("evento : "+evento);	
		
		String sqlEvento = "";
		//insertando en alarmas paso de gps temperatura
		sqlEvento = "insert into aperturas(imei,tipoEvento,fecha,latitud,longitud,velocidad) values('"
				+ simcard + "', '" + event + "', '" + sqlDate + "', " + latitude + ", " + longitude +", " + speed + ")";
		
		logger.info("apertura evento: " + sqlEvento);
		try {
			synchronized (this) {
			Statement Evento=null;
			try {
					if (conexionTsensorFinal.isClosed()){
						conexionTsensorFinal = DriverManager.getConnection(DATABASE_TSENSOR_FINAL,
								USER_TSENSOR_FINAL, PASS_TSENSOR_FINAL); }
					Evento= conexionTsensorFinal.createStatement();
				
				
					Evento.execute(sqlEvento);
					Evento.close();
			
					return true;
			
				} catch (Exception e) {
					logger.error("Error en conexion a base de datos: "+e);
					return false;
				}
			
			}
		} catch (Exception e) {
			e.printStackTrace();logger.info("evento: catch "+e);
			return false;
			
		}
	
}
	public boolean saveTemperature(String simcard, String sensor, String temp, String date, String companyId, Connection connection) {
		logger.info("Simcard = " + simcard + " Sensor = " + sensor + " temp = " + temp + " date = " + date + " companyId = " + companyId);
		long timeZoneInMilliseconds = getTimeZoneByCompanyId(companyId, connection) * 60l * 1000l * 60l;
		Date fecha = stringToDate(date);
		fecha.setTime(fecha.getTime() + timeZoneInMilliseconds);
		java.sql.Timestamp sqlDate = new java.sql.Timestamp(fecha.getTime());
		
		Date vHoy = new Date();
		long vDiferenciaMin = (fecha.getTime() - vHoy.getTime()) / (60 * 1000);
		// Si la diferencia es mayor a un a~no significa que el dato est'a con
		// problemas.
		int minutosEnUnAnho = 535600; // 1 año
		int minutosEnDiezHoras = 600; // diez horas
		if (Math.abs(vDiferenciaMin) > minutosEnUnAnho) {
			logger.error("Error al procesar Cadena Antares, dato con fecha " + fecha + " del sensor "
					+ sensor + ", equipo " + simcard);
			return false;
		}
		if (vDiferenciaMin > minutosEnDiezHoras) {
			logger.error("Error: Dato Futuro equipo : "+simcard);

			return false;
		}
		
		// *****************************************+Pruebas del nuevo modulo de alarmas
		String sqlAlarma = "";
		//insertando en alarmas paso de gps temperatura
		sqlAlarma = "insert into alarmas_paso_gps(pi_dispositivo,pi_temp,pi_fecha,tipo_medicion,id) values('"
				+ sensor + "', " + temp + ", '" + sqlDate + "', " + 0 + ", " + 1 + ")";

		try {
			Statement stAlarmaPaso;
			stAlarmaPaso = conexionTsensorFinal.createStatement();
			synchronized (this) {
				stAlarmaPaso.execute(sqlAlarma);
				stAlarmaPaso.close();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		//*********************************************** Pruebas del nuevo modulo de alarmas
		
		// Se calcula diferencia de Hora por cambios de horario.
		String SQL2 = "";
		try {
			synchronized (this) {
				Statement s2 = null;
				try {
					if (conexionTsensorFinal.isClosed())
						conexionTsensorFinal = DriverManager.getConnection(DATABASE_TSENSOR_FINAL,
								USER_TSENSOR_FINAL, PASS_TSENSOR_FINAL);
					s2 = conexionTsensorFinal.createStatement();
				} catch (Exception e) {
					logger.error("Error en conexion a base de datos.");
				}

				SQL2 = "INSERT INTO refrigeracion_datos (codigo_sensor, simcard, valor, fecha)";
				SQL2 = SQL2 + " VALUES('" + sensor + "', '" + simcard + "', " + temp + ", '"
						+ sqlDate + "')";

				s2.execute(SQL2);
				s2.close();
				return true;
			}
		} catch (Exception e) {
			logger.error("Error insertando dato a la tabla: ID " + sensor + ", SQL: " + SQL2);
			return false;
		}
	}

	public int getTimeZoneByCompanyId (String companyId, Connection connection) {
		logger.info("companyId = " + companyId);
		int timeZone = -3;
		try {
//			Connection dataBaseConnection = DriverManager.getConnection(DATABASE_GPS, USER_GPS, PASS_GPS);
			Statement st = connection.createStatement();
			ResultSet rs = st.executeQuery("SELECT \"timezone\" FROM \"Company\" where \"id\" = " + companyId);
			if (rs.next()) {
				timeZone = rs.getInt("timezone");
				logger.debug("TimeZone = " + timeZone);
			}
			rs.close();
			st.close();
		} catch (SQLException ex) {
			logger.error(ex.getMessage());
		}
		return timeZone;
	}

	public static Date stringToDate (String fecha) {
		logger.info("fecha = " + fecha);
		fecha = fecha.trim();
		logger.debug("stringToDate: Fecha entregada = " + fecha);
		SimpleDateFormat formatDatos = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date date = null;
		try {
			date = formatDatos.parse(fecha);
		} catch (ParseException e) {
			logger.error(e.getMessage());
		}
		return date;
	}
				}
