package gpsweb.parser;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

enum parseStatus {

    /*
     * Estados numerados para el resultado del parsing
     * de la tramaTaip
     * 0 : Dato está en la base de datos sin procesar por parser.
     * 1 : OK, parsing y almacenado en BD.
     * 3 : Error : Genérico no conocido desconocido,
     * 4 : Error: IMEI del movil no registrado en la BD.
     * 5 : Error: Posicion (0,0) no permitida.
     * 6 : Error: Posicion fuere de rango para  longitud rango [-180,180] y latitud rango [-90,90].
     *
     */
    NOT_PROC(0), OK(1), BAD_FORMAT(2), UNKNOWN_ERROR(3), UNKNOWN_MOBILE(4), ZERO_POSITION(5), INVALID_RANGE_POS(6);
    private final int code;

    private parseStatus(int code_) {
        this.code = code_;
    }

    public int getCode() {
        return code;
    }
};

public class GpsEvent {

    /* imei o mobileid */
    String deviceID = null;
    /* Tipo de Trama Taip REV01, RTXE02, etc */
    String typeTAIP = "01";
    /* Id de movil en la base de datos tabla Mobile */
    Integer movilId = null;
    /* Latitud geografica */
    Float latitude = null;
    /* Longitud geografica */
    Float longitude = null;
    /* Fecha GPS en GMT */
    String gpsDate = null;
   /* Angulo de giro del movil respecto al Norte geografico*/
    Float direction = null;
    /* Velocidad del movil en Km/Hr */
    Float speed = null;
    /* Nivel de señal gps */
    Float signalStrength = null;
    /* Tipo de evento en base de datos */
    String eventid = null;
    /* Edad del datos gps, ver manual dispositivo */
    Integer age = null;
    /* Id del dato taip en base de datos, el cual sera el id del evento gps */
    Long frameId = null;
    /* Id de la empresa asociada al movil , evento */
    Integer companyId;

    /* Id de la geocerca, en caso de que no se envie, queda en null*/
    Integer geo_id=null;
    /* Boolean si es que el evento es de entrada o de salida de la geocerca*/
    Boolean in_geo=null;

    /*
     * Parametros opcionales ocupar cuando se requiera en caso de tener
     * eventos con parametros que necesiten ser persistidos.
     * Para ello mirar TaipParserWorker el cual interactua con las BD.
     *
     */
    HashMap<String, String> optionalParams = new HashMap<String, String>();
    parseStatus status = parseStatus.UNKNOWN_ERROR;

    /*
     *Representacion del los parametros opcionales como arreglo de
     * String[i] => {"llave", "valor"}
     *
     */
    public String[][] optionalParametersAsArray() {
        String opts[][] = null;
        if (optionalParams.size() > 0) {
            opts = new String[optionalParams.size()][2];
        } else {
            return null;
        }
        int index = 0;
        Iterator it = optionalParams.entrySet().iterator();
        Map.Entry<String, String> optionalParam;
        while (it.hasNext()) {
            optionalParam = (Map.Entry<String, String>) it.next();
            opts[index][0] = optionalParam.getKey();
            opts[index][1] = optionalParam.getValue();
            index++;
        }
        return opts;
    }
}
