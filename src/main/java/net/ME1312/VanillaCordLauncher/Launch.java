package net.ME1312.VanillaCordLauncher;

import net.ME1312.VanillaCordLauncher.Library.URLOverrideClassLoader;
import net.ME1312.VanillaCordLauncher.Library.Version.Version;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Launch {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Args: <version>");
            return;
        }

        Version launcherversion = new Version(1, 6);
        Version mcversion = new Version(args[0].toLowerCase());
        Version patchversion = null;

        System.out.println("VanillaCord launcher v" + launcherversion);
        System.out.println("Searching versions");

        JSONObject mcprofile = null;
        JSONObject mcversionmanifest = new JSONObject(readAll(new BufferedReader(new InputStreamReader(new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json").openStream(), Charset.forName("UTF-8")))));
        for (int i = 0; i < mcversionmanifest.getJSONArray("versions").length(); i++) {
            if (mcversionmanifest.getJSONArray("versions").getJSONObject(i).getString("id").equals(mcversion.toString())) {
                mcprofile = new JSONObject(readAll(new BufferedReader(new InputStreamReader(new URL(mcversionmanifest.getJSONArray("versions").getJSONObject(i).getString("url")).openStream(), Charset.forName("UTF-8")))));
                mcversion = new Version(mcprofile.getString("id"));
                break;
            }
        } if (mcprofile == null) throw new IllegalArgumentException("Could not find server version for " + mcversion);

        String patchkey = null;
        JSONObject patchversionmanifest = new JSONObject(readAll(new BufferedReader(new InputStreamReader(new URL("https://raw.githubusercontent.com/ME1312/VanillaCord/master/version_manifest.json").openStream(), Charset.forName("UTF-8")))));
        for (String version : patchversionmanifest.keySet()) {
            if ((patchversion == null || patchversion.compareTo(new Version(patchversionmanifest.getJSONObject(version).getString("id"))) < 0) && mcversion.compareTo(new Version(version)) >= 0) {
                patchversion = new Version(patchversionmanifest.getJSONObject(version).getString("id"));
                patchkey = version;
            }
        } if (patchversion == null || patchkey == null) throw new IllegalArgumentException("Could not find patches for " + mcversion);
        JSONObject patchprofile = new JSONObject(readAll(new BufferedReader(new InputStreamReader(new URL(patchversionmanifest.getJSONObject(patchkey).getString("url")).openStream(), Charset.forName("UTF-8")))));
        patchversion = new Version(patchprofile.getString("id"));
        if (launcherversion.compareTo(new Version(patchprofile.getString("launcherVersion"))) < 0) throw new IllegalStateException("Branch " + patchversion + " requires VanillaCord Launcher v" + patchprofile.getString("launcherVersion") + '+');


        File mcfile = new File("in/" + mcversion + ".jar");
        mcfile.getParentFile().mkdirs();
        if (!mcfile.exists()) {
            System.out.println("Downloading Minecraft Server " + mcversion);
            try (
                    FileOutputStream fin = new FileOutputStream(mcfile);
                    ReadableByteChannel rbc = Channels.newChannel(new URL(mcprofile.getJSONObject("downloads").getJSONObject("server").getString("url")).openStream())
            ) {
                fin.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                if (mcfile.length() != mcprofile.getJSONObject("downloads").getJSONObject("server").getLong("size"))
                    throw new IllegalStateException("Downloaded file does not match the profile's expectations: File size: " + mcfile.length() + " != " + mcprofile.getJSONObject("downloads").getJSONObject("server").getLong("size"));
                String sha1 = sha1(mcfile);
                if (!mcprofile.getJSONObject("downloads").getJSONObject("server").getString("sha1").equals(sha1))
                    throw new IllegalStateException("Downloaded file does not match the profile's expectations: SHA-1 checksum: " + sha1 + " != " + mcprofile.getJSONObject("downloads").getJSONObject("server").getString("sha1"));
            } catch (Throwable e) {
                mcfile.delete();
                throw e;
            }
        }

        File patchfile = new File("in/" + patchversion + "-patch.jar");
        patchfile.getParentFile().mkdirs();
        if (!patchfile.exists()) {
            System.out.println("Downloading patches from branch " + patchversion);
            try (
                    FileOutputStream fin = new FileOutputStream(patchfile);
                    ReadableByteChannel rbc = Channels.newChannel(new URL(patchprofile.getJSONObject("download").getString("url")).openStream())
            ) {
                fin.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            } catch (Throwable e) {
                patchfile.delete();
                throw e;
            }
        } else System.out.println("Reusing patches from branch " + patchversion);

        URLOverrideClassLoader loader = new URLOverrideClassLoader(new URL[]{patchfile.toURI().toURL(), mcfile.toURI().toURL()});
        loader.loadClass(patchprofile.getString("launch")).getDeclaredMethod("main", String[].class).invoke(null, (Object) args);
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
