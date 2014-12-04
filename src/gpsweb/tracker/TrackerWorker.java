package gpsweb.tracker;

import gpsweb.parser.Log;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;

/**
 *
 * @author dbl

 **/
class TrackerWorker implements Runnable {

    private static ArrayList<Maintenance> getMaintenanceList(Connection dataBaseConnection) {

        ArrayList<Maintenance> list = new ArrayList<Maintenance>();
        Log.getLogger().debug("Getting mobileList...");
        try {
            Statement st = dataBaseConnection.createStatement();

            ResultSet rs = st.executeQuery("select id,\"mobileId\", COALESCE(lastevent,0) as lastevent FROM \"Maintenance\" where id IN  (select MAX(id) from \"Maintenance\" GROUP by \"mobileId\") ");
            while (rs.next()) {
                list.add(new Maintenance(rs.getInt("id"), rs.getInt("mobileId"), rs.getInt("lastevent")));
                Log.getLogger().debug("Maintenance --> id : " + rs.getInt("id") + "  mobileId : " + rs.getString("mobileId"));
            }
            rs.close();
            st.close();
        } catch (Exception e) {
            System.exit(-1);
        }
        return list;
    }

    private static Double lastgetMileage(Connection conn, Integer mobileId, Integer currEvent, Integer lastEvent) {
        StringBuilder sql = new StringBuilder("WITH T AS((SELECT g.id, g.longitude as y1 , g.latitude as x1 ,lead(longitude) OVER (ORDER BY id ASC) as y2 , lead(latitude)");
        sql.append("OVER (ORDER BY id ASC) as x2 FROM \"GpsEvent\" g where \"mobileId\" = ");
        sql.append(mobileId).append(" AND id >= ").append(currEvent).append(" AND id <=").append(lastEvent).append(" ORDER BY id DESC)) Select ROUND(SUM( haversine(T.x1,T.y1,T.x2,T.y2)))");
        sql.append("as distance FrOM  T WHERE x2 is not NULL AND y2 is not NULL");

        /*System.out.println(sql.toString());*/
        Double distance = null;

        try {
            Statement st = conn.createStatement();

            ResultSet rs = st.executeQuery(sql.toString());
            while (rs.next()) {
                distance = rs.getDouble("distance");
                Log.getLogger().debug(String.format("Distance  --> id : %d  distance : %f  (cur: %d , last: %d ) ", mobileId, distance, currEvent, lastEvent));
            }
            rs.close();
            st.close();
        } catch (Exception e) {
            Log.getLogger().fatal("Execption trayendo distancia", e);
            System.exit(-1);
        }
        return distance;
    }

    public void run() {

        ArrayList<Maintenance> list = null;
        Connection connection = null;
        try {
            connection = Main.getConnection();
            list = getMaintenanceList(connection);
            Double distance = null;
            Integer currlastEvent, nextLastEvent = null;
            for (Maintenance man : list) {
                if (man != null) {
                    currlastEvent = man.getLastEventId();
                    nextLastEvent = getLastEvent(connection, man.getMobileid());

                    if (nextLastEvent > currlastEvent) {
                        distance = lastgetMileage(connection, man.getMobileid(), currlastEvent, nextLastEvent);

                        if (distance != null) {
                            setcurrentMileage(connection, man.getId(), nextLastEvent, distance);
                        } else {
                            Log.getLogger().debug(" No distance tracked for mobile Id --> id : " + System.currentTimeMillis());
                        }

                    }

                } else {
                    Log.getLogger().debug(" No maitenance found for mobile Id --> id : " + man);


                }
            }
        } catch (Exception ex) {

            Log.getLogger().error("Excepcion Tracker --> ", ex);
            System.out.println("");
        }

    }

    public static void main(String args[]) {
    }

    private static Integer getLastEvent(Connection connection, Integer mobileid) {
        String qlastEvent = "select MAX(id) as id from \"GpsEvent\" WHERE \"mobileId\" ='" + mobileid + "'";
        Integer lastEvent = 0;
        try {
            Statement st = connection.createStatement();

            ResultSet rs = st.executeQuery(qlastEvent);
            while (rs.next()) {
                lastEvent = rs.getInt("id");
                Log.getLogger().debug("Distance  --> id : " + rs.getInt("id") + "  mobileId : " + mobileid);
            }
            rs.close();
            st.close();
        } catch (Exception e) {
            Log.getLogger().fatal("Se pudo traer last Event para mobile id = " + mobileid, e);
            System.exit(-1);

        }
        return lastEvent;

    }

    private static Integer setcurrentMileage(Connection connection, Integer id, Integer nextLastEvent, Double distance) {
        String updateMaintenance = "UPDATE \"Maintenance\" SET \"currentMileage\"=\"currentMileage\" + '" + distance + "', lastevent = '" + nextLastEvent + "' WHERE id = '" + id + "'";
        Integer update = -1;
        try {
            Log.getLogger().debug("SQL : " + updateMaintenance);

            Statement st = connection.createStatement();
            update = st.executeUpdate(updateMaintenance);
            st.close();
        } catch (Exception e) {

            Log.getLogger().fatal("No se pudo actualizar lastEvent,distancia para mobile id = " + id, e);
            System.exit(-1);
        }
        return update;
    }
}
