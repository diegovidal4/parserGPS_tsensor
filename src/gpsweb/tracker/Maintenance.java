package gpsweb.tracker;

public class Maintenance {

    public Maintenance(int id, int mobileId, int lastEventId) {
        this.id = id;
        this.Mobileid = mobileId;
        this.lastEventId = lastEventId;
    }

    public Integer getMobileid() {
        return Mobileid;
    }

    public void setMobileid(Integer Mobileid) {
        this.Mobileid = Mobileid;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(Integer lastEventId) {
        this.lastEventId = lastEventId;
    }
    Integer id;
    Integer lastEventId;
    Integer Mobileid;
}
