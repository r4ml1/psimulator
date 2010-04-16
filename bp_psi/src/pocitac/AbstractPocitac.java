/*
 * Udelat:
 * Vraceni chyboveho paketu na vyprseni ttl - HOTOVO
 * Predelani ethernetovyho posilani na dve metody - HOTOVO
 * Nastaveni preposilani a ip_forward - ZBYVA zjistit, co se dela, kdyz neni preposilani nastaveny, zatim
 * to proste zahazuje
 *
 * odesliEthernetove() - pro odeslani host unreachable vymyslet odesilani se spravnou adresou, zatim se posila prvni.
 * odesliNovejPaket() - to samy
 */
package pocitac;

import datoveStruktury.*;
import datoveStruktury.RoutovaciTabulka;
import java.util.ArrayList;
import java.util.List;
import prikazy.AbstraktniPing;

/**
 * Virtualni pocitac, predek Linuxu a Cisca
 * @author neiss
 */
public abstract class AbstractPocitac {

    private boolean ladeni=true;

    // promenny pro technicky zabezpeceni pocitace:
    public Komunikace komunikace;

    public List<SitoveRozhrani> rozhrani; //kvuli vypisum to musi bejt verejny
    public String jmeno; //jmeno pocitace
    public RoutovaciTabulka routovaciTabulka;
    /**
     * Je-li true, preposilaj se pakety, jinak ne.
     * Obsah linuxoveho souboru /proc/sys/net/ipv4/ip_forward 0=false, 1=true
     * Pro cisco defaultne true, pro linux false.
     */
    public boolean ip_forward = true; //defaultne nastaveno, linux si to v konstruktoru prepise

    @Deprecated
    public AbstractPocitac(String jmeno) {
        vypis("Pouziva se deprecated metoda AbstractPocitac(String jmeno)");
        komunikace = new Komunikace(3567, this);
        rozhrani = new ArrayList<SitoveRozhrani>();
        this.jmeno = jmeno;
    }

    public AbstractPocitac(String jmeno, int port) {
        this.jmeno = jmeno;
        rozhrani = new ArrayList<SitoveRozhrani>();
        komunikace = new Komunikace(port, this);
        routovaciTabulka = new RoutovaciTabulka();
    }

    @Deprecated
    public AbstractPocitac(int port) {
        vypis("Pouziva se deprecated metoda AbstractPocitac(int port)");
        komunikace = new Komunikace(port, this);
        rozhrani = new ArrayList<SitoveRozhrani>();
    }

    /**
     * Prida rozhrani iface do seznamu rozhrani.
     * @param iface
     */
    public void pridejRozhrani(SitoveRozhrani iface) {
        rozhrani.add(iface);
    }

    @Deprecated
    public void nastavJmeno(String jm) {
        vypis("Pouziva se deprecated metoda nastavJmeno(String jm)");
        this.jmeno = jm;
    }

    public SitoveRozhrani najdiRozhrani(String jmeno) {
        if (jmeno == null) {
            return null;
        }
        for (SitoveRozhrani rozhr : rozhrani) {
            if (rozhr.jmeno.equals(jmeno)) {
                return rozhr;
            }
        }
        return null;
    }

    /**
     * Tahle metoda vypisuje na standartni vystup. Pouzivat pro vypisy v Komunikaci, Konsoli i Parseru atd.
     * pro snadnejsi debugovani, aby se vedelo, co kterej pocitac dela.
     * @param ret
     */
    public void vypis(String ret) {
        System.out.println("(" + jmeno + ":) " + ret);
    }


    // zatim pomocna metoda, pak se muze smazat
    public void vypisRozhrani() {

        for (SitoveRozhrani iface : rozhrani) {
            System.out.println("(" + jmeno + ":) " + iface.jmeno);
            if (iface.vratPrvni() != null) {
                System.out.println("(" + jmeno + ":) " + iface.vratPrvni().vypisAdresu());
                System.out.println("(" + jmeno + ":) " + iface.vratPrvni().vypisMasku());
            }
            System.out.println("(" + jmeno + ":) " + iface.macAdresa);
            if (iface.pripojenoK != null) {
                System.out.println("(" + jmeno + ":) " + iface.pripojenoK.jmeno);
            }
            System.out.println("(" + jmeno + ":)");
        }
    }


//****************************************************************************************************
//tady zacinaj metody pro posilani pingu:

/**
 * Všeobecný poznámky k posílání paketů:
 * Ethernetová (linková) vrstva:
 * - všechny pakety se posílaj metodou odesliEthernetove() a prijimaj prijmiEthernetove()
 * - IpAdresa sousedni (v metode prijmiEthernetove ocekavana) - IpAdresa pro jeden skok
 *      - kdyz se posila podle routy na branu, je to adresa brany
 *      - kdyz se posila podle routy na rozhrani, je to cilova adresa paketu
 * Síťová (IP) vrstva:
 * - všechny nový pakety se posílaj metodou odesliNovejPaket(...)
 *   všechny pakety k přeposílání se přeposílaj metodou preposliPaket(...)
 * - metody začínající slovem posli sloužej k odesílání novejch paketů na vyšší úrovni, nespecifikujou se třeba
 *   všechny parametry; všechny ale interne používaj metodu odesliNovejPaket(...)
 */
    int default_ttl=64; //defaultni ttl
    /**
     * Tahle metoda hleda, jestli mezi myma rozhranima neni nejaky se zadanou adresou
     * @param cil
     * @return
     */
    protected SitoveRozhrani najdiMeziRozhranima(IpAdresa cil) {
        for (SitoveRozhrani rozhr : rozhrani) {
            if (rozhr.obsahujeStejnouAdresu(cil)) {
                return rozhr;
            }
        }
        return null;
    }

    /**
     * Zkousi ethernetove poslat paket vedlejsimu pocitaci, ten ho bud prijme, nebo ho neprijme
     * a pak musim zpatky poslat host unreachable.
     * @param p paket, kterej posilam
     * @param rozhr rozhrani toho sousedniho pocitace, na kterej to posilam
     * @param sousedni IP adresa rozhrani na sousednim pocitaci, na kterej to posilam
     */
    private void odesliEthernetove(Paket p, SitoveRozhrani rozhr, IpAdresa sousedni) {
        if (rozhr != null) { //cizi rozhrani by teoreticky mohlo bejt null
            if ( rozhr.getPc().prijmiEthernetove(p, rozhr, sousedni) ){ //adresa souhlasi
                //paket odeslan
            }else{//adresa nesouhlasi, zpatky se musi poslat host unreachable
                posliNovejPaketOdpoved(p,rozhr.pripojenoK.vratPrvni(), 3, 1); //host unreachable
                            // -> svoji adresu musim dost krkolome zjistovat, ale je to asi nejjednodussi
                            //    a nemusim se bat, ze tam nekde bude null
            }
        }else{
            //na druhym konci kabelu nikdo neposloucha - paket se ale povazuje za odeslanej
            //a zpatky se nic neposila
        }
    }

    /**
     * Ethernetove prijima nebo odmita me poslany pakety.
     * @param p
     * @param rozhr rozhrani pocitace, kterej ma paket prijmoutm, tzn. tohodle pocitace
     * @param ocekavana adresa, kterou na rozhrani ocekavam
     * @return true, kdyz byl paket prijmut, jinak false
     */
    public abstract boolean prijmiEthernetove(Paket p, SitoveRozhrani rozhr, IpAdresa ocekavana);


    /**
     * Slouzi k odeslani odpovedi - odesila icmp reply nebo host unreachable. V odpovednim paketu
     * se pouzije jako zdrojova adresa cilova adresa puvodniho paketu.
     * @param puvodni puvodni paket, na kterej se odpovida
     * @param spec_zdroj kdyz chci specifikovat, s jakym zdrojem se ma paket poslat
     * @param typ typ paketu
     * @param kod kod paketu
     */
    private void posliNovejPaketOdpoved(Paket puvodni,IpAdresa spec_zdroj, int typ, int kod){
        if(spec_zdroj!=null){
            odesliNovejPaket(spec_zdroj, puvodni.zdroj, typ,
                    kod,puvodni.cas,puvodni.icmp_seq,default_ttl,puvodni.prikaz);
        }else{
            odesliNovejPaket(puvodni.cil, puvodni.zdroj, typ,
                    kod,puvodni.cas,puvodni.icmp_seq,default_ttl,puvodni.prikaz);
        }
    }

    /**
     * Slouzi k poslání novyho pingu z tohodle pocitace, musi vytvorit paket a doplnit do nej adresu zdroje.
     * Sama nic neposila, pouziva metodu odesliNovejPaket, s tim, ze nespecifikuje specialni zdroj.
     * @param cil
     * @param typ
     * @param kod
     * @param cas
     * @param icmp_seq
     * @param prikaz
     * @return false - ping se nepodarilo odeslat <br />
     *          true - ping byl odeslan
     */
    @Deprecated // udelal jsem specifictejsi metody
    public boolean posliNovejPaket(IpAdresa cil,int typ,int kod,double cas,int icmp_seq,
            int ttl, AbstraktniPing prikaz) {
        return odesliNovejPaket(null, cil, typ, kod, cas, icmp_seq, ttl, prikaz);
    }

    /**
     * Slouzi k poslání novyho pingu z tohodle pocitace, musi vytvorit paket a doplnit do nej adresu zdroje.
     * Sama nic neposila, pouziva metodu odesliNovejPaket, s tim, ze nespecifikuje specialni zdroj.
     * @param cil
     * @param icmp_seq kdyz je -1, nic se neodesle, jenom zkusebni
     * @param ttl
     * @param prikaz
     * @return true - naslo se vhodny rozhrani, ping byl odeslan <br />
     *         false - ping se nepodarilo odeslat, nenaslo se vhodny rozhrani 
     */
    public boolean posliIcmpRequest(IpAdresa cil, int icmp_seq, int ttl, AbstraktniPing prikaz){
        int typ=8; //icmp request
        int kod=0;
        double cas = 0;
        return odesliNovejPaket(null, cil, typ, kod, cas, icmp_seq, ttl, prikaz);
    }

    public boolean posliNetUnreachable(IpAdresa cil, double cas, int icmp_seq, int ttl, AbstraktniPing prikaz){
        int typ=3; //paket nedosel
        int kod=0; //net unreachable
        return odesliNovejPaket(null, cil, typ, kod, cas, icmp_seq, ttl, prikaz);
    }

    public boolean posliTimeExceeded(IpAdresa cil, double cas, int icmp_seq, int ttl, AbstraktniPing prikaz){
        int typ=11; //paket nedosel
        int kod=0; //net unreachable
        return odesliNovejPaket(null, cil, typ, kod, cas, icmp_seq, ttl, prikaz);
    }

    /**
     * Slouzi k odeslani novyho paketu z tohodle pocitace, ne k preposilani. Touto metodou se
     * odesílaj všechny nový pakety z thodle počítače. Metoda najde spravny rozhrani,
     * kterym se ma paket  poslat, v pripade, ze spec_zdroj je null se paket odesle s adresou
     * tohoto rozhrani, jinak se odesle s adresou spec_zdroj. K odesilani pouziva metodu odesliEthernetove().
     * @param spec_zdroj IP adresa zdroje, kdyz ji chci natvrdo zadat
     * @param cil
     * @param typ
     * @param kod
     * @param cas
     * @param icmp_seq kdyz je -1, tak se nic neposle, jenom zkusebni, jestli o pujde
     * @param ttl
     * @param prikaz
     * @return false - ping se nepodarilo odeslat, nenaslo se vhodny rozhrani <br />
     *          true - naslo se vhodny rozhrani, ping byl odeslan
     */
    private boolean odesliNovejPaket(IpAdresa spec_zdroj, IpAdresa cil, int typ, int kod,
            double cas, int icmp_seq, int ttl, AbstraktniPing prikaz) {

        IpAdresa zdroj; //IP, ktera bude jako adresa zdroje v paketu
        SitoveRozhrani mojeRozhr; //rozhrani, pres ktery budu paket posilat
        SitoveRozhrani ciziRozhr=null; //rozhrani, na ktery budu paket posilat
        IpAdresa sousedni=cil;//defaultne (kdyz si to posilam sobe, nebo kdyz to posilam na routu na rozhrani (U))

       //hledani rozhrani, pres ktery se to bude posilat:
        mojeRozhr = najdiMeziRozhranima(cil);//nejdriv se hleda cil mezi mejma adresama
        if (mojeRozhr != null) { //cilova adresa se nasla mezi adresama na mejch rozhranich -> posilam sam sobe
            ciziRozhr = mojeRozhr;
        } else { //kdyz adresa neni moje, zkousim hladat v routovaci tabulce
            RoutovaciTabulka.Zaznam z = routovaciTabulka.najdiSpravnejZaznam(cil);
            if (z != null) { //nejaky zaznam se nasel
                mojeRozhr = z.getRozhrani(); //nesmi existovat zaznam bez rozhrani
                ciziRozhr = mojeRozhr.pripojenoK;
                if (z.getBrana() != null) { //kdyz je zaznam na branu, sousedni adresa musi bejt adresa brany
                    sousedni = z.getBrana();
                } //... jinak je sousedni adresa rovnou cilova adresa toho paketu
            }
        }
        if (mojeRozhr == null) { //kdyz nenajdu spavny rozhrani ani v routovaci tabulce, vratim false
            return false;
        }
        zdroj = mojeRozhr.vratPrvni();
        if(spec_zdroj!=null){ //kdyz je specifikovano, s jakym zdrojem se ma paket poslat, tak se tak posle
            zdroj=spec_zdroj;
        }
        Paket paket = new Paket(zdroj, cil, typ, kod, cas, icmp_seq, ttl, prikaz);
        if(paket.icmp_seq != -1 && //to signalisuje, ze se paket nema odeslat, ale jen se zkousi, jestli to pujde
                paket.zdroj != null ) { //rozhrani, kterym to chci poslat nema prirazenou IP adresu
            if(ladeni)vypis("posilam novej paket na rozhrani "+mojeRozhr.jmeno+" na sousedni adresu "
                        +sousedni.vypisAdresu()+" "+paket.toString());
            odesliEthernetove(paket, ciziRozhr, sousedni);
        }
        return true;
    }

    /**
     * Slouzi k preposilani paketu. Neni-li paket kam dorucit, posle se zpatky zprava, ze nelze dorucit.
     * @param paket
     */
    private void preposliPaket(Paket paket) {
        IpAdresa sousedni = paket.cil; //adresa nejblizsiho pocitace, kam se ma paket poslat, defaultne cil,
        //kdyztak se to zmeni na branu z routovaci tabulky
        paket.ttl -= 1;
        if (paket.ttl == 0) {
            posliTimeExceeded(paket.zdroj, paket.cas, paket.icmp_seq, default_ttl, paket.prikaz);
            return;
        } else {
            RoutovaciTabulka.Zaznam z = routovaciTabulka.najdiSpravnejZaznam(paket.cil);
            if (z != null) { //zaznam nalezen
                SitoveRozhrani rozhr = z.getRozhrani();
                if (z.getBrana() != null) {
                    sousedni = z.getBrana(); //sousedni uzel je brana z routovaci tabulky
                }
                if (ladeni) {
                    vypis("preposilam paket na rozhrani " + rozhr.jmeno + " na sousedni adresu " + sousedni.vypisAdresu() + " " + paket.toString());
                }
                odesliEthernetove(paket, rozhr.pripojenoK, sousedni);
            } else {//rozhrani nenalezeno - paket neni kam poslat
                posliNetUnreachable(paket.zdroj, paket.cas, paket.icmp_seq, default_ttl, paket.prikaz);
                // -> net unreachable
            }
        }
    }

    /**
     * Prijima ping. Je-li urcen pro mne, udela patricnou akci (odesle odpoved nebo vypise vypis). Neni-li
     * urcen pro me, posle paket dal.
     * @param paket
     */
    protected void prijmiPaket(Paket paket) {
        if(ladeni)vypis("prijal jsem paket "+paket.toString());
        paket.cas += Math.random()*0.03 + 0.07; //nejnizsi hodnota asi 0.07 ms, nejvyssi 0.1 ms
        SitoveRozhrani rozhr = najdiMeziRozhranima(paket.cil);
        if (rozhr != null) { //paket je u me v cili
            if(paket.typ==8){ //icmp request
                posliNovejPaketOdpoved(paket,null, 0, 0); //zpatky se posila icmp reply
            }else { //paket je urcen pro me
                paket.prikaz.zpracujPaket(paket);
            }
        } else { // paket se musi poslat dal
            if (ip_forward){ // nastaveno preposilani - v souboru /proc/sys/net/ipv4/ip_forward je jednicka
                preposliPaket(paket);
            }else{
                // Jestli se nepletu, tak paket proste zahodi. Chce to ale jeste overit.
            }
        }
    }

}
