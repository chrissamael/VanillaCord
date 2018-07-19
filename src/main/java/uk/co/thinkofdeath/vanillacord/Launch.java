package uk.co.thinkofdeath.vanillacord;

import com.google.common.io.Resources;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Launch {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Args: <version>");
            return;
        }

        System.out.println("VanillaCord branch 1.7.10");
        System.out.println("Searching versions");

        String mcversion = args[0].toLowerCase();
        JSONObject mcprofile = null;
        JSONObject mcversionmanifest = new JSONObject(readAll(new BufferedReader(new InputStreamReader(new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json").openStream(), Charset.forName("UTF-8")))));
        for (int i = 0; i < mcversionmanifest.getJSONArray("versions").length(); i++) {
            if (mcversionmanifest.getJSONArray("versions").getJSONObject(i).getString("id").equals(mcversion.toString())) {
                mcprofile = new JSONObject(readAll(new BufferedReader(new InputStreamReader(new URL(mcversionmanifest.getJSONArray("versions").getJSONObject(i).getString("url")).openStream(), Charset.forName("UTF-8")))));
                break;
            }
        } if (mcprofile == null) throw new IllegalArgumentException("Could not find server version for " + mcversion);


        File in = new File("in/" + mcversion + ".jar");
        in.getParentFile().mkdirs();
        if (!in.exists()) {
            System.out.println("Downloading Minecraft Server " + mcversion);
            try (FileOutputStream fin = new FileOutputStream(in)) {
                Resources.copy(new URL(mcprofile.getJSONObject("downloads").getJSONObject("server").getString("url")), fin);

                if (in.length() != mcprofile.getJSONObject("downloads").getJSONObject("server").getLong("size"))
                    throw new IllegalStateException("Downloaded file does not match the profile's expectations: File size: " + in.length() + "!=" + mcprofile.getJSONObject("downloads").getJSONObject("server").getLong("size"));
                String sha1 = sha1(in);
                if (!mcprofile.getJSONObject("downloads").getJSONObject("server").getString("sha1").equals(sha1))
                    throw new IllegalStateException("Downloaded file does not match the profile's expectations: SHA-1 checksum: " + sha1 + "!=" + mcprofile.getJSONObject("downloads").getJSONObject("server").getString("sha1"));
            } catch (Throwable e) {
                in.delete();
                throw e;
            }
        }

        URLOverrideClassLoader loader = new URLOverrideClassLoader(new URL[]{Launch.class.getProtectionDomain().getCodeSource().getLocation(), in.toURI().toURL()});
        loader.loadClass("uk.co.thinkofdeath.vanillacord.Main").getDeclaredMethod("main", String[].class).invoke(null, (Object) args);
    }

    private static String sha1(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int len = input.read(buffer);

            while (len != -1) {
                sha1.update(buffer, 0, len);
                len = input.read(buffer);
            }

            byte[] digest = sha1.digest();
            StringBuilder output = new StringBuilder();
            for (int i=0; i < digest.length; i++) {
                output.append(Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1));
            }
            return output.toString();
        }
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }
}