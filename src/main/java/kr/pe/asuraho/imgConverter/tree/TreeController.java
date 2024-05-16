package kr.pe.asuraho.imgConverter.tree;

import jakarta.annotation.PostConstruct;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
public class TreeController {
    @Value("default.images")
    private String images;

    @Value("default.width")
    private String defaultWidth;

    @Value("default.height")
    private String defaultHeight;

    /***
     * todo : spring application utf-8 이슈 해결 못함
     */
    private String mainPath = "D:/기타타타";

    private String regexp;

    @PostConstruct
    public void setRegexp() {
        regexp = "^([\\s]+(\\.(?i)(" + images + "))$)";
    }

    final private SimpleDateFormat yyyymmddhhmmss = new SimpleDateFormat("yyyy.MM.dd hh:mm:ss");
    final private SimpleDateFormat yyyymmdd = new SimpleDateFormat("yyyy.MM.dd");

    @GetMapping(value = "tree")
    public String tree(Model model) {

        model.addAttribute("mainPath", mainPath);
        model.addAttribute("images", images);
        model.addAttribute("defaultWidth", defaultWidth);
        model.addAttribute("defaultHeight", defaultHeight);

        return "tree/index";
    }


    @PostMapping("/tree/list")
    @ResponseBody
    public List<Map<String, String>> list(@RequestBody Map<String, String> model) {
        List<Map<String, String>> result = new ArrayList<>();
        File path = new File(model.get("fullPath"));

        boolean isFileLength = Boolean.valueOf(model.get("isFileLength"));
        boolean isFileCnt = Boolean.valueOf(model.get("isFileCnt"));

        try (Stream<Path> pathStream = Files.list(path.toPath())) {
            List<File> list = pathStream.map(Path::toFile).filter(File::isDirectory).sorted().collect(Collectors.toList());

            for (File file : list) {
                Map<String, String> map = new HashMap<>();
                map.put("path", file.getName());
                map.put("fullPath", (file.getParent() + File.separator + file.getName()).replace("\\", "/"));
                map.put("lastModified", yyyymmddhhmmss.format(new Date(file.lastModified())));

                if (isFileLength || isFileCnt) {
                    long[] info = folderInfo(file, isFileLength, isFileCnt);
                    map.put("size", String.valueOf(info[0]));
                    map.put("cnt", String.valueOf(info[1]));
                }
                result.add(map);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    private long[] folderInfo(File directory, boolean isFileLength, boolean isFileCnt) {

        long length = 0;
        long cnt = 0;

        File[] listFiles = directory == null ? null : directory.listFiles();

        if (directory == null || listFiles == null || listFiles.length == 0) return new long[]{0, 0};
        for (File file : listFiles) {
            if (file.isFile() && file.getName().matches(regexp)) {
                if (isFileLength) length += file.length();
                if (isFileCnt) cnt++;

            } else {
                long[] result = folderInfo((file), isFileLength, isFileCnt);
                if (isFileLength) length += result[0];
                if (isFileCnt) cnt += result[1];
            }
        }

        return new long[]{length, cnt};
    }

    @PostMapping("/tree/imgConversion")
    @ResponseBody
    public Map<String, String> imgConversion(@RequestBody Map<String, String> model) {
        boolean recursive = Boolean.valueOf(model.get("recursive"));
        boolean overwrite = Boolean.valueOf(model.get("overwrite"));
        int width = Integer.valueOf(model.get("width"));
        int height = Integer.valueOf(model.get("height"));

        String overwriteStr = overwrite ? "" : "_" + yyyymmdd.format(new Date());

        File path = new File(model.get("fullPath"));
        int cntConversion = 0;
        try (Stream<Path> pathStream = Files.list(path.toPath())) {
            List<File> list = pathStream.map(Path::toFile).filter(File::isFile).collect(Collectors.toList());
            for (File file : list) {
                if (conversion(file, overwriteStr, width, height)) cntConversion++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (recursive) {
            try (Stream<Path> pathStream = Files.list(path.toPath())) {
                List<File> list = pathStream.map(Path::toFile).filter(File::isDirectory).collect(Collectors.toList());

                for (File file : list) {
                    cntConversion += folderConversion(file, overwriteStr, width, height);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        int finalCntConversion = cntConversion;
        return new HashMap<>() {{
            put("cntConversion", String.valueOf(finalCntConversion));
        }};

    }

    private int folderConversion(File directory, String overwriteStr, int width, int height) throws IOException {
        int cnt = 0;
        File[] listfiles = directory == null ? null : directory.listFiles();
        if (directory == null || listfiles == null || listfiles.length == 0) return 0;
        for (File file : listfiles) {
            if (file.isFile() && file.getName().matches(regexp)) {
                if (conversion(file, overwriteStr, width, height)) cnt++;
            } else {
                cnt += folderConversion(file, overwriteStr, width, height);
            }
        }
        return cnt;
    }

    private boolean conversion(File file, String overwriteStr, int width, int height) throws IOException {
        if (!file.getName().matches(regexp)) return false;
        String fileName = file.getName();
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1);
        BufferedImage originalImage = ImageIO.read(file);

        if (originalImage.getWidth() > width || originalImage.getHeight() > height) {
            BufferedImage resize = Scalr.resize(originalImage, Scalr.Method.AUTOMATIC, Scalr.Mode.AUTOMATIC, width, height, Scalr.OP_ANTIALIAS);

            return ImageIO.write(resize, ext, new File(file.getParent() + File.separator + fileName.substring(0, fileName.lastIndexOf(".")) + overwriteStr + "." + ext));
        }

        return false;
    }


}
