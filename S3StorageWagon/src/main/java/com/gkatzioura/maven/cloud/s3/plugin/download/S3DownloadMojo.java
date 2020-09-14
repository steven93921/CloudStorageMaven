/*
 * Copyright 2018 Emmanouil Gkatziouras
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gkatzioura.maven.cloud.s3.plugin.download;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.wagon.authentication.AuthenticationException;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.gkatzioura.maven.cloud.KeyIteratorConcated;
import com.gkatzioura.maven.cloud.s3.EndpointProperty;
import com.gkatzioura.maven.cloud.s3.PathStyleEnabledProperty;
import com.gkatzioura.maven.cloud.s3.plugin.PrefixKeysIterator;
import com.gkatzioura.maven.cloud.s3.utils.S3Connect;

@Mojo(name = "s3-download")
public class S3DownloadMojo extends AbstractMojo {

    @Parameter( property = "s3-download.bucket")
    private String bucket;

    @Parameter(property = "s3-download.keys")
    private List<String> keys;

    @Parameter(property = "s3-download.downloadPath")
    private String downloadPath;

    @Parameter(property = "s3-download.region")
    private String region;

    @Parameter(property = "s3-download.stripPrefix")
    private boolean stripPrefix;

    @Parameter(property = "s3-download.profile")
    private String profile;

    @Parameter(property = "s3-download.credentials")
    private BasicAWSCredentials credentials;


    private static final String DIRECTORY_CONTENT_TYPE = "application/x-directory";

    private static final Logger LOGGER = Logger.getLogger(S3DownloadMojo.class.getName());

    public S3DownloadMojo() {
    }

    public S3DownloadMojo(String bucket, List<String> keys, String downloadPath, String region) {
        this.bucket = bucket;
        this.keys = keys;
        this.downloadPath = downloadPath;
        this.region = region;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        AmazonS3 amazonS3;

        try {
            S3Connect s3Connect = new S3Connect(region);
            if (credentials != null) {
                s3Connect.setCredentials(credentials);
            } else if (profile != null) {
                s3Connect.setCredentialsProfile(profile);
            }
            amazonS3 = s3Connect.connect();
        } catch (AuthenticationException e) {
            throw new MojoExecutionException(
                    String.format("Unable to authenticate to S3 with the available credentials. Make sure to either define the environment variables or System properties defined in https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html.%n" +
                            "Detail: %s", e.getMessage()),
                    e);
        }

        if (keys.size()==1) {
            String key = keys.get(0);
            if (key.endsWith("/") || stripPrefix) {
                // Skip Prefix should always use '/' for all directory type prefixes ?
                downloadPrefix(amazonS3, key);
            } else {
                downloadFile(amazonS3, key, downloadPath);
            }
        } else {
            for (String key : keys) {
                downloadPrefix(amazonS3, key);
            }
       }
    }

    private void downloadPrefix(AmazonS3 amazonS3, String prefix) {
        Iterator<String> keyIterator = new PrefixKeysIterator(amazonS3, bucket, prefix);
        while (keyIterator.hasNext()) {
            String key = keyIterator.next();
            String target = stripPrefix ?
                                downloadPath + "/" + key.substring(prefix.length()) :
                                downloadPath + "/" + key;
            downloadFile(amazonS3, key, target);
        }
    }

    private void downloadFile(AmazonS3 amazonS3, String key, String target) {
        File file = new File(target);

        if(file.getParent()!=null) {
            file.getParentFile().mkdirs();
        }

        S3Object s3Object = amazonS3.getObject(bucket, key);

        if(isDirectory(s3Object)) {
            return;
        }

        try(S3ObjectInputStream s3ObjectInputStream = s3Object.getObjectContent();
            FileOutputStream fileOutputStream = new FileOutputStream(file)
        ) {
            IOUtils.copy(s3ObjectInputStream,fileOutputStream);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not download s3 file");
            e.printStackTrace();
        }
    }

    private final boolean isDirectory(S3Object s3Object) {
        return s3Object.getObjectMetadata().getContentType().equals(DIRECTORY_CONTENT_TYPE);
    }


}
