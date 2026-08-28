package com.idata.service.files;

import com.idata.dto.FileManageVO;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HDFS 文件存取：上传返回完整 URI，删除按 URI 执行。
 * 通过 {@link FileSystem#get(URI, Configuration)} 走 Hadoop 缓存，线程安全；
 * 同一套 FileSystem API 兼容 file://（本地开发验证）与 hdfs://（生产）。
 */
@Service
public class HdfsStorageService {

    private static final Logger log = LoggerFactory.getLogger(HdfsStorageService.class);

    private final String hdfsUri;
    private final String storagePath;

    public HdfsStorageService(
            @Value("${idata.files.hdfs-uri:hdfs://localhost:8020}") String hdfsUri,
            @Value("${idata.files.storage-path:/idata/files}") String storagePath) {
        this.hdfsUri = hdfsUri;
        this.storagePath = storagePath;
    }

    /** 上传到 {hdfs-uri}{storage-path}/{yyyyMMdd}/{uuid}-{name}，返回完整 URI */
    public String upload(MultipartFile file) throws IOException {
        return store(file.getOriginalFilename(), file.getInputStream());
    }

    /** 字节数组上传（UDF 打包产物等），文件名清洗规则与 upload 一致 */
    public String uploadBytes(byte[] data, String fileName) throws IOException {
        return store(fileName, new ByteArrayInputStream(data));
    }

    private String store(String originalName, InputStream in) throws IOException {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String safeName = sanitizeFileName(originalName);
        String relative = storagePath.replaceFirst("^/+", "");
        String uri = hdfsUri + "/" + relative + "/" + day + "/" + UUID.randomUUID() + "-" + safeName;
        Path target = new Path(uri);
        try (FileSystem fs = fs(); InputStream input = in) {
            fs.mkdirs(target.getParent());
            try (FSDataOutputStream out = fs.create(target, false)) {
                input.transferTo(out);
            }
        }
        log.info("上传文件到 HDFS: {}", uri);
        return uri;
    }

    /** 按完整 URI 打开文件输入流（调用方负责关闭）；文件不存在抛 FileNotFoundException */
    public FSDataInputStream download(String fileUri) throws IOException {
        Path path = new Path(fileUri);
        FileSystem fs = FileSystem.get(path.toUri(), conf());
        if (!fs.exists(path)) {
            throw new java.io.FileNotFoundException("文件不存在: " + fileUri);
        }
        return fs.open(path);
    }

    /** 列出文件管理存储目录下递归所有 .jar（含非经文件管理上传、运维直接 put 的），filePath 为完整 URI */
    public List<FileManageVO> listJars() throws IOException {
        String root = hdfsUri + "/" + storagePath.replaceFirst("^/+", "");
        Path rootPath = new Path(root);
        List<FileManageVO> jars = new ArrayList<>();
        try (FileSystem fs = FileSystem.get(rootPath.toUri(), conf())) {
            if (!fs.exists(rootPath)) {
                return jars;
            }
            RemoteIterator<LocatedFileStatus> it = fs.listFiles(rootPath, true);
            while (it.hasNext()) {
                LocatedFileStatus st = it.next();
                String name = st.getPath().getName();
                if (!name.toLowerCase().endsWith(".jar")) {
                    continue;
                }
                FileManageVO vo = new FileManageVO();
                vo.setFileName(name);
                vo.setFilePath(st.getPath().toUri().toString());
                vo.setFileSize(st.getLen());
                vo.setFileExt("jar");
                jars.add(vo);
            }
        }
        jars.sort((a, b) -> a.getFileName().compareToIgnoreCase(b.getFileName()));
        return jars;
    }

    /** 按完整 URI 删除 HDFS 文件 */
    public void delete(String fileUri) throws IOException {
        if (fileUri == null || fileUri.isBlank()) {
            return;
        }
        FileSystem fs = FileSystem.get(URI.create(fileUri), conf());
        if (fs.delete(new Path(fileUri), false)) {
            log.info("删除 HDFS 文件: {}", fileUri);
        }
    }

    private FileSystem fs() throws IOException {
        return FileSystem.get(URI.create(hdfsUri), conf());
    }

    private Configuration conf() {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", hdfsUri);
        return conf;
    }

    /** 去掉路径分隔符等非法字符，防止路径注入 */
    private String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "unnamed";
        }
        String base = original;
        int slash = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
        if (slash >= 0) {
            base = original.substring(slash + 1);
        }
        return base.replaceAll("[\\s/\\\\:*?\"<>|]", "_");
    }
}
