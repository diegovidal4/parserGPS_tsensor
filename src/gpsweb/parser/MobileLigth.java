package gpsweb.parser;

public class MobileLigth {


    Integer mobileId;
    Integer companyId;

    public MobileLigth(int mobileid , int companyid ) {
        this.mobileId = mobileid;
        this.companyId = companyid;
    }

    public Integer getmobileId(){
        return mobileId;
    }

     public Integer getcompanyId(){
        return companyId;
    }
}
