/*
 * Released under the MIT License.
 * Copyright 2020 Oscar Vega-Gisbert.
 */
package pifmweb;

import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.reflect.*;

//************************************************************************
public class Main
{
private static String  m_resource, m_music[], m_mp3[][];
private static Process m_process;

private static final int    PORT  = 8080;
private static final String GET   = "GET ",
                            HOME  = "/home/pi",
                            //HOME  = "/home/oscar",
                            MUSIC = HOME +"/Music/";

//------------------------------------------------------------------------
public static void main(String[] args) throws Exception
{
    System.setOut(new PrintStream(new FileOutputStream("pifm.out")));
    System.setErr(new PrintStream(new FileOutputStream("pifm.err")));

    Runtime.getRuntime().addShutdownHook(new Thread(Main::killProcess));
    loadMusic();
    ServerSocket serverSocket = new ServerSocket(PORT);

    for(;;)
    {
        try(Socket socket = serverSocket.accept();
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream())))
        {
            String resource = getResource(in);

            if(resource != null)
            {
                if("favicon.ico".equals(resource))
                    runGetBin(socket, resource);
                else
                    runGetHtml(socket, resource);
            }
        }
        catch(Exception ex)
        {
            System.err.println("ERROR: "+ ex);
            ex.printStackTrace(System.err);
        }
    }
}

//------------------------------------------------------------------------
private static String getResource(BufferedReader in) throws IOException
{
    String request = in.readLine();

    if(request == null)
        return null; //.............................................RETURN

    request = request.trim();

    if(!request.startsWith(GET))
        return "error"; //..........................................RETURN

    String r = request.substring(GET.length()).trim();
    r = r.substring(0, r.indexOf(' '));

    if(r.equals("/"))
        return r;
    else if(r.startsWith("/"))
        return r.substring(1);
    else
        return r;
}

//------------------------------------------------------------------------
private static void runGetBin(Socket socket, String resource)
    throws IOException
{
    /*
    try(InputStream is = Main.class.getResourceAsStream(resource);
        DataOutputStream out = new DataOutputStream(
                socket.getOutputStream()))
    {
        out.writeBytes("HTTP/1.0 200 OK\n");
        out.writeBytes("Content-Type: image/x-icon\n");
        out.writeBytes("Content-Length: " + data.length);
        out.writeBytes("\n\n");
        out.write(data);
    }*/
}

//------------------------------------------------------------------------
private static void runGetHtml(Socket socket, String resource)
    throws IOException
{
    try(BufferedWriter out = new BufferedWriter(
            new OutputStreamWriter(socket.getOutputStream())))
    {
        out.write("HTTP/1.1 200 OK\n");
        out.write("Content-Type: text/html; charset=UTF-8\n");
        out.write("\n"); // Si no ponemos este '\n' no funciona.
        out.write("<!DOCTYPE html>\n");
        out.write("<html><head><title>Pi FM web</title>\n");

        out.write("<meta name='viewport' ");
        out.write("content='width=device-width, ");
        out.write("initial-scale=1, maximum-scale=1, ");
        out.write("minimum-scale=1, user-scalable=no'>");

        out.write("<meta name='mobile-web-app-capable' ");
        out.write("content='yes'>");

        out.write("<style>body { background-color: #000; ");
        out.write("font-family: sans-serif; ");
        out.write("color: #FFF; }");
        out.write("a { outline: 0; color: #0F0; ");
        out.write("text-decoration: none; }");
        out.write("</style>");

        out.write("</head><body>\n");
        runGetBody(out, resource);
        out.write("</body></html>\n");
    }
}

//------------------------------------------------------------------------
private static void runGetBody(Writer out, String resource)
    throws IOException
{
    try
    {
        int i = resource.indexOf('?');

        if(i != -1)
        {
            String op = resource.substring(i + 1);
            resource = resource.substring(0, i);

            if("stop".equals(op))
                killProcess();
        }

        switch(resource)
        {
            case "":
            case "/":
                menu(out);
                break;

            case "radio-paradise-main":
                exec(out, "http://stream.radioparadise.com/mp3-192");
                menu(out);
                break;

            case "radio-paradise-rock":
                exec(out, "http://stream.radioparadise.com/rock-192");
                menu(out);
                break;

            case "radio-paradise-mellow":
                exec(out, "http://stream.radioparadise.com/mellow-192");
                menu(out);
                break;
                
            case "radio-78-synth":
                exec(out, "http://cc6.beheerstream.com:8151/stream");
                menu(out);
                break;

            default:
                menuMp3(out, resource);
        }

        if(m_process == null)
            m_resource = null;
    }
    catch(Exception ex)
    {
        out.write("<span style='color:#F00;'><b>ERROR"+ ex +"</b><span>");
        System.err.println("ERROR: "+ ex);
        ex.printStackTrace(System.err);
    }
}

//------------------------------------------------------------------------
private static void writeTitle(Writer out) throws IOException
{
    out.write("<center><h1>Pi FM web</h1>\n");

    if(m_process != null && m_resource != null)
    {
        String resource = m_resource;

        if(resource.startsWith(MUSIC))
        {
            resource = resource.substring(MUSIC.length());
            int iBar = resource.indexOf('/');

            resource = resource.substring(0, iBar) +"<br>"+
                       resource.substring(iBar + 1);

            String mp3 = "/*.mp3";

            if(resource.endsWith(mp3))
            {
                resource = resource.substring(0,
                           resource.length() - mp3.length());
            }
        }

        if(!resource.startsWith("http"))
            resource = resource.replace("/", "<br>");

        out.write("<p>"+ resource +"\n");

        write(out, "?stop",
              "<span style='color: #F00;'><b>S T O P</b><span>");
    }

    out.write("</center>\n");
}

//------------------------------------------------------------------------
private static void menu(Writer out) throws IOException
{
    writeTitle(out);
    write(out, "/radio-paradise-main",   "Radio Paradise Main");
    write(out, "/radio-paradise-rock",   "Radio Paradise Rock");
    write(out, "/radio-paradise-mellow", "Radio Paradise Mellow");
    write(out, "/radio-78-synth",        "Radio 78 Synth");

    out.write("<p><b>Música:</b>");

    for(int i = 0; i < m_music.length; i++)
        write(out, "/"+ i, m_music[i]);
}

//------------------------------------------------------------------------
private static void menuMp3(Writer out, String resource)
    throws IOException
{
    StringTokenizer st = new StringTokenizer(resource, "/");

    int i = Integer.parseInt(st.nextToken()),
        j = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : -1,
        k = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : -1;

    File folder,
         mp3[] = null;

    if(j != -1)
    {
        folder = new File(MUSIC + m_music[i] +"/"+ m_mp3[i][j]);
        mp3    = folder.listFiles((d, n) -> n.endsWith(".mp3"));
        Arrays.sort(mp3, (a, b) -> a.compareTo(b));
        String path = MUSIC + m_music[i] +"/"+ m_mp3[i][j];

        if(k != -1)
            exec(out, path +"/"+ mp3[k].getName());
        else
            exec(out, path +"/*.mp3");
    }

    writeTitle(out);
    write(out, "/", "Menú principal");
    out.write("<br>&nbsp;");
    write(out, "/"+ i, "<b>"+ m_music[i] +"</b>");

    if(j == -1)
    {
        for(j = 0; j < m_mp3[i].length; j++)
            write(out, "/"+ i +"/"+ j, m_mp3[i][j]);
    }
    else
    {
        write(out, "/"+ i +"/"+ j, m_mp3[i][j]);
        out.write("<br>&nbsp;");

        for(k = 0; k < mp3.length; k++)
        {
            if(m_process == null)
                write(out, "/"+ i +"/"+ j +"/"+ k, mp3[k].getName());
            else
                out.write("<p>"+ mp3[k].getName());
        }
    }
}

//------------------------------------------------------------------------
private static void write(Writer out, String recurso, String titulo)
    throws IOException
{
    out.write("<p><a href='"+ recurso +"'>"+ titulo +"</a>");
}

//------------------------------------------------------------------------
private static synchronized void exec(Writer out, String resource)
    throws IOException
{
    if(m_resource == null)
    {
        m_process = Runtime.getRuntime().exec(
                new String[]{ HOME +"/bin/pifm", resource });

        m_resource = resource;
    }
}

//------------------------------------------------------------------------
private static void killProcess()
{
    try
    {
        if(m_process == null || !m_process.isAlive())
            return; //..............................................RETURN

        long pid = getPid(m_process);

        if(pid == 0)
            return; //..............................................RETURN

        Process p = Runtime.getRuntime().exec(new String[]{
                    "/bin/kill", Long.toString(pid) });

        p.waitFor();
    }
    catch(Exception ex)
    {
        RuntimeException rex = new RuntimeException(ex.toString());
        rex.setStackTrace(ex.getStackTrace());
        throw rex;
    }
    finally
    {
        m_process  = null;
    }
}

//------------------------------------------------------------------------
public static long getPid(Process p)
{
    long pid = 0;

    try
    {
        if(p.getClass().getName().equals("java.lang.UNIXProcess"))
        {
            Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            pid = f.getLong(p);
            f.setAccessible(false);
        }
    }
    catch(Exception e)
    {
        pid = 0;
    }

    return pid;
}

//------------------------------------------------------------------------
private static void error(Writer out) throws IOException
{
    out.write("<center><h1 style='color: #F00;'>");
    out.write("Recurso no encontrado</h1></center>\n");
}

//------------------------------------------------------------------------
private static void loadMusic()
{
    FilenameFilter filter = (dir, name) -> !name.startsWith(".");
    File homeMusic = new File(MUSIC);
    m_music = homeMusic.list(filter);

    if(m_music == null)
        m_music = new String[0];

    Arrays.sort(m_music, (a,b) -> a.compareToIgnoreCase(b));
    m_mp3 = new String[m_music.length][];

    for(int i = 0; i < m_music.length; i++)
    {
        String[] m = new File(homeMusic, m_music[i]).list(filter);

        if(m == null)
            m = new String[0];
        else
            Arrays.sort(m, (a,b) -> a.compareToIgnoreCase(b));

        m_mp3[i] = m;
    }
}

} // Main
