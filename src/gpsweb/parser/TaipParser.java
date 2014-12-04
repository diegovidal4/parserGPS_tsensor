package gpsweb.parser;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import org.apache.log4j.Logger;

public class TaipParser {

	private static Logger logger = Logger.getLogger(TaipParser.class);
//	static final float MILES2KM = 1.690f;
	static final float MILES2KM = 1.609f; //correccion de millas a k/h
	static final String opParamSeparator = ";";
	static final String startTaip = ">";
	static final String stopTaip = "<";
	static final String opkeyValSep = "=";
	static final java.text.SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	static final java.text.SimpleDateFormat formato_sistema = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	static final HashMap<String, String> allowedFrames = new HashMap<String, String>();

	static {
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

		allowedFrames.put("REV00", "POSITION");
		allowedFrames.put("REV02", "VEHICLE_ON");
		allowedFrames.put("REV03", "VEHICLE_OFF");
		allowedFrames.put("REV04", "ENERGIZED");
		allowedFrames.put("REV05", "ENERGY_LOSS");
    allowedFrames.put("REV06", "GEO"); //Evento geocerca
		allowedFrames.put("REV11", "ENTRY1_ACTIVE");
		allowedFrames.put("REV12", "ENTRY1_INACTIVE");
		allowedFrames.put("REV13", "ENTRY2_ACTIVE");
		allowedFrames.put("REV14", "ENTRY2_INACTIVE");
		allowedFrames.put("REV15", "ENTRY3_ACTIVE");
		allowedFrames.put("REV16", "ENTRY3_INACTIVE");
		allowedFrames.put("REV17", "ENTRY4_ACTIVE");
		allowedFrames.put("REV18", "ENTRY5_INACTIVE");
		allowedFrames.put("REV22", "DIRECTION_CHANGE");
		allowedFrames.put("REV19", "OUTPUT1_ACTIVE");
		allowedFrames.put("REV20", "OUTPUT1_INACTIVE");
		allowedFrames.put("REV21", "OUTPUT2_ACTIVE");
		allowedFrames.put("REV27", "OUTPUT2_INACTIVE");
		allowedFrames.put("REV23", "OUTPUT3_ACTIVE");
		allowedFrames.put("REV24", "OUTPUT3_INACTIVE");
		allowedFrames.put("REV25", "OUTPUT4_ACTIVE");
		allowedFrames.put("REV26", "OUTPUT4_INACTIVE");
		allowedFrames.put("REV30", "CRASH");
		allowedFrames.put("REV31", "SPEEDING");
		allowedFrames.put("REV40", "SAFE_ENGINE_TURN_OFF");
		allowedFrames.put("REV32", "STOPPAGE");
		allowedFrames.put("REV33", "TRANSMISION_EACH_100_METERS");

		/* Temperature */

		allowedFrames.put("RTXEV01", "TEMPERATURE");
		allowedFrames.put("RTXEV11", "ENTRY1_ACTIVE_TMP");
		allowedFrames.put("RTXEV12", "ENTRY1_INACTIVE_TMP");
		allowedFrames.put("RTXEV13", "ENTRY2_ACTIVE_TMP");
		allowedFrames.put("RTXEV14", "ENTRY2_INACTIVE_TMP");
		allowedFrames.put("RTXEV15", "ENTRY3_ACTIVE_TMP");
		allowedFrames.put("RTXEV16", "ENTRY3_INACTIVE_TMP");

		allowedFrames.put("RTX01", "TEMPERATURE");
		allowedFrames.put("RTX11", "ENTRY1_ACTIVE_TMP");
		allowedFrames.put("RTX12", "ENTRY1_INACTIVE_TMP");
		allowedFrames.put("RTX13", "ENTRY2_ACTIVE_TMP");
		allowedFrames.put("RTX14", "ENTRY2_INACTIVE_TMP");
		allowedFrames.put("RTX15", "ENTRY3_ACTIVE_TMP");
		allowedFrames.put("RTX16", "ENTRY3_INACTIVE_TMP");
	}

	public static GpsEvent parseTaip(String frame) {
		int startStdFrame = frame.indexOf(startTaip);
		int endStdFrame = frame.indexOf(opParamSeparator);
		String standardFrame = null;
		String opFrame = null;
		String fecha_one_wire = null;
		String fecha_gps = null;
		String fecha_sistema = null;
		String espacio = " ";
		String fecha_mision = "1980-01-06 00:00:00";
		String gps_day2 = null;
		String year_gps = null;
		String year_actual = null;
		String year_onewire = null;
		String fecha_ow2 = null;
		GpsEvent event = new GpsEvent();
		try {
			if (endStdFrame < 0) {
				return event;
			}
			if (startStdFrame >= 0) {
				standardFrame = frame.substring(startStdFrame + 1, endStdFrame); //
				opFrame = frame.substring(endStdFrame + 1).replaceAll(stopTaip, "");
        System.out.println(opFrame);
			}

			/*
			 * Largos supuestos y minimos de las trama parte estanadard
			 * y la otra opcional.
			 *
			 */

			if (standardFrame == null || standardFrame.length() < 10 || opFrame == null || opFrame.length() < 5) {
				event.status = parseStatus.BAD_FORMAT;
				return event;
			}

			Iterator<String> iterator = allowedFrames.keySet().iterator();
			while (iterator.hasNext()) {
				String aFrame = iterator.next();
				if (standardFrame.startsWith(aFrame)) {
					String KVarray[] = opFrame.split(opParamSeparator);
					String KYval[] = null;
					for (String kv : KVarray) {
						//;params1;
						if (kv.indexOf(opkeyValSep) > 0) {
							KYval = kv.split(opkeyValSep);
							if ("ID".equals(KYval[0])) {
								event.deviceID = KYval[1].trim();
							} else if ("FOW".equals(KYval[0])) { //captura la hora del one wire
								String hora_ow = KYval[1].trim();
								String weeks3 = hora_ow.substring(0, 4);
								String day3 = hora_ow.substring(4, 5);
								String seconds3 = hora_ow.substring(5, 10);

								int weeks2 = Integer.parseInt(weeks3);
								int day2 = Integer.parseInt(day3);
								int seconds2 = Integer.parseInt(seconds3);
								int fecha2 = seconds2 + 86400 * (day2 + (7 * weeks2));

								fecha_one_wire = getTimestampGMT(fecha2);
								String fecha_ow = fecha_one_wire;
								logger.info(" FOW fecha ow:" + fecha_ow);
							}
              else if("RE".equals(KYval[0]))
              {
                String data[]=KYval[1].trim().split("K");
                event.in_geo=Boolean.parseBoolean(data[0]);
                event.geo_id=Integer.parseInt(data[1]);
                System.out.println(event.geo_id);
                System.out.println(event.in_geo);
              }else {
								event.optionalParams.put(KYval[0], KYval[1]);
							}
						} else {
							event.status = parseStatus.BAD_FORMAT;
							return event;
						}
					}
					//event.typeTAIP = standardFrame.substring(aFrame.length(), aFrame.length() + 2);
					event.typeTAIP = aFrame;
					//System.out.println("type Taip" + event.typeTAIP);
					//System.out.println("tipo " + event.typeTAIP);
					standardFrame = standardFrame.substring(aFrame.length());
					try {
						int weeks = Integer.parseInt(standardFrame.substring(0, 4));
						int day = Integer.parseInt(standardFrame.substring(4, 5));
						int seconds = Integer.parseInt(standardFrame.substring(5, 10));
						seconds = seconds + 86400 * (day + (7 * weeks));

						fecha_gps = getTimestampGMT(seconds);
						Date fecha_actual = new Date();
						fecha_sistema = formato_sistema.format(fecha_actual);
						String fecha_sistema2[] = fecha_sistema.split(espacio);
						String fecha_sistema3 = fecha_sistema2[0].trim();
						String yearSistema[] = fecha_sistema3.split("-");
						year_actual = yearSistema[0].trim(); //año actual del servidor
						if (fecha_one_wire == null) {
							event.gpsDate = fecha_gps;
						} else {
							String sin_espacio = fecha_one_wire.trim();
							String remplaza = sin_espacio.replaceAll(" ", "#");
							String fecha_ow[] = remplaza.split("#");
							fecha_ow2 = fecha_ow[0].trim();
							String yearOnewire[] = fecha_ow2.split("-");
							year_onewire = yearOnewire[0].trim();

							String sin_espacio2 = fecha_gps.trim();
							String remplaza2 = sin_espacio2.replaceAll(" ", "#");
							String gps_day[] = remplaza2.split("#");
							gps_day2 = gps_day[0].trim();
							String Separa_fecha_gps[] = gps_day2.split("-");
							year_gps = Separa_fecha_gps[0].trim();
							logger.info("fecha gps: " + gps_day2 + " anho gps: " + year_gps + " fecha actual: " + fecha_sistema3 + " IMEI GPS:" + event.deviceID);

							if (fecha_mision.equals(fecha_gps)) {
								event.gpsDate = fecha_one_wire;
							} else {

								if (year_actual.equals(year_gps)) {//verifica si el gps tiene el año actual
									event.gpsDate = fecha_gps;
								} else if (year_actual.equals(year_onewire)) {
									event.gpsDate = fecha_one_wire;
								} else {

									event.gpsDate = fecha_gps;

								}
							}
						}

						logger.info("Gps: " + fecha_gps + " one wire: " + fecha_one_wire + " final para registrar" + event.gpsDate + "IMEI GPS :" + event.deviceID);
//						logger.info("Hora final para registrar"+event.gpsDate+" IMEI GPS :"+event.deviceID);
						//    System.out.println("DATE " +event.gpsDate );
						String num = standardFrame.substring(10, 18);
						if (num.charAt(0) == '+') {
							num = num.substring(1);
						}
						event.latitude = Integer.parseInt(num) / 100000f;
						num = standardFrame.substring(18, 27);
						if (num.charAt(0) == '+') {
							num = num.substring(1);
						}



						event.longitude = Integer.parseInt(num) / 100000f;


						/* Chequeo de integridad de la posicion no permitida (0,0) */

						if (event.latitude == 0f && event.longitude == 0f) {
							event.status = parseStatus.ZERO_POSITION;
						}


						/*
						 * Chequeo de integridad de la posicion LAT [-90,09]
						 * LNG [-180,180].
						 *
						 */

						if (event.latitude < -90 || event.latitude > 90) {
							event.status = parseStatus.INVALID_RANGE_POS;
						}

						if (event.longitude < -180 || event.longitude > 180) {
							event.status = parseStatus.INVALID_RANGE_POS;
						}


						event.speed = Integer.parseInt(standardFrame.substring(27, 30)) * MILES2KM;
						event.direction = Float.parseFloat(standardFrame.substring(30, 33));
						event.signalStrength = Float.parseFloat(standardFrame.substring(33, 34));
						event.age = Integer.parseInt(standardFrame.substring(34, 35));
						event.status = parseStatus.OK;
						event.eventid = allowedFrames.get(aFrame);
						//  System.out.println("weeks" + weeks + "days " + day + " secs " + seconds + "lat " + lat + " lat " + lng + " vel " + vel);
						//  System.out.println(" dir " + direct + " src " + src + "age " + age);
					} catch (Exception e) {
						event.status = parseStatus.BAD_FORMAT;
						e.printStackTrace(System.err);
					}
					return event;
				}
			}
			return event;
		} catch (Exception e) {
			event.status = parseStatus.BAD_FORMAT;
			e.printStackTrace(System.err);
			return event;
		}
	}

	public static String getTimestampGMT(int seconds) {
		Calendar cal = Calendar.getInstance(new SimpleTimeZone(0, "GMT"));
		cal.set(Calendar.YEAR, 1980);
		cal.set(Calendar.MONTH, 0);
		cal.set(Calendar.DAY_OF_MONTH, 6);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.add(Calendar.SECOND, seconds);
		return sdf.format(cal.getTime());
	}

	public static void main(String args[]) {

    GpsEvent n = parseTaip(">REV001821450975-3339673-0706921500000012;IO=200;SV=8;BL=4418;CV09=0;AD=8;AL=+511;ID=356612023066451");
		//GpsEvent e = parseTaip(">REV061633270676+0000000+0000000000100090;IO=000;SV=7;BL=4195;CV09=0;AD=0;AL=+603;ID=356612021220159");
		System.out.println(String.format(" %f %f ", n.latitude, n.longitude));
    System.out.println(n.eventid);
    System.out.println(n.in_geo);
	}
}
