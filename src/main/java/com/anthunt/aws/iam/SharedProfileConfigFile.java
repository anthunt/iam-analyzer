package com.anthunt.aws.iam;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfilesConfigFile;
import com.amazonaws.auth.profile.internal.AllProfiles;
import com.amazonaws.auth.profile.internal.BasicProfile;
import com.amazonaws.auth.profile.internal.BasicProfileConfigLoader;
import com.amazonaws.auth.profile.internal.ProfileAssumeRoleCredentialsProvider;
import com.amazonaws.auth.profile.internal.ProfileProcessCredentialsProvider;
import com.amazonaws.auth.profile.internal.ProfileStaticCredentialsProvider;
import com.amazonaws.auth.profile.internal.securitytoken.ProfileCredentialsService;
import com.amazonaws.auth.profile.internal.securitytoken.STSProfileCredentialsServiceLoader;
import com.amazonaws.profile.path.AwsProfileFileLocationProvider;
import com.amazonaws.util.ValidationUtils;

public class SharedProfileConfigFile extends ProfilesConfigFile {

    private final List<File> profileFiles;
    private final ProfileCredentialsService profileCredentialsService;
    
    private final ConcurrentHashMap<String, AWSCredentialsProvider> credentialProviderCache = new ConcurrentHashMap<String, AWSCredentialsProvider>();
    private volatile Map<String, AllProfiles> allProfiles = new HashMap<String, AllProfiles>();
    private volatile long profileFileLastModified;

    public SharedProfileConfigFile() throws SdkClientException {
        this(getProfileFiles());
    }

    private static List<File> getProfileFiles() {
    	List<File> files = new ArrayList<File>();
    	files.add(getCredentialProfilesFile());
    	files.add(getConfigProfilesFile());
    	return files;
    }

    private SharedProfileConfigFile(List<File> files) throws SdkClientException {
        this(files, STSProfileCredentialsServiceLoader.getInstance());
    }

    private SharedProfileConfigFile(List<File> files, ProfileCredentialsService credentialsService) throws
            SdkClientException {

    	profileFiles = new ArrayList<File>();
    	
        profileCredentialsService = credentialsService;
        
        for(File file : files) {
        	File profileFile = ValidationUtils.assertNotNull(file, "profile file");
	        profileFiles.add(profileFile);
	        profileFileLastModified = file.lastModified();
	        allProfiles.put(profileFile.getName(), loadProfiles(profileFile));
        }
    }

    private AllProfiles getMergedAllProfiles() {

    	Map<String, BasicProfile> profiles = new HashMap<String, BasicProfile>();
    	
    	Map<String, Map<String, String>> basicProfiles = new HashMap<String, Map<String,String>>();
    	
    	for(AllProfiles allProfile : allProfiles.values()) {
        	Map<String, BasicProfile> profilesMap = allProfile.getProfiles();
        	Set<String> keys = profilesMap.keySet();
        	for(String key : keys) {
        		BasicProfile basicProfile = profilesMap.get(key);
        		if(basicProfiles.containsKey(key)) {
        			
        			Map<String, String> profileProperties = new HashMap<String, String>(basicProfiles.get(key));
        			Map<String, String> properties = basicProfile.getProperties();
        			for(String propertiesKey : properties.keySet()) {
        				if(!profileProperties.containsKey(propertiesKey)) {
        					profileProperties.put(propertiesKey, properties.get(propertiesKey));
        				}
        			}
        			basicProfiles.put(key, profileProperties);
        			
        		} else {
        			basicProfiles.put(key, basicProfile.getProperties());
        		}
        	}
        }
    	
    	for(String profileName : basicProfiles.keySet()) {
    		profiles.put(profileName, new BasicProfile(profileName, basicProfiles.get(profileName)));
    	}
    	
    	return new AllProfiles(profiles);
    }
    
    /**
     * Returns the AWS credentials for the specified profile.
     */
    public AWSCredentials getCredentials(String profileName) {
        final AWSCredentialsProvider provider = credentialProviderCache.get(profileName);
        if (provider != null) {
            return provider.getCredentials();
        } else {
        	
        	AllProfiles allProfiles = getMergedAllProfiles();
        	
            BasicProfile profile = null;
            
        	profile = allProfiles.getProfile(profileName);
        	if(profile == null) {
        		profile = allProfiles.getProfile("profile " + profileName);
        	}
            
            if (profile == null) {
                throw new IllegalArgumentException("No AWS profile named '" + profileName + "'");
            }
            final AWSCredentialsProvider newProvider = fromProfile(profile);
            credentialProviderCache.put(profileName, newProvider);
            return newProvider.getCredentials();
        }
    }

    /**
     * Reread data from disk.
     */
    public void refresh() {
    	
    	for(File profileFile : profileFiles) {
	        if (profileFile.lastModified() > profileFileLastModified) {
	            synchronized (this) {
	                if (profileFile.lastModified() > profileFileLastModified) {
	                    allProfiles.put(profileFile.getName(), loadProfiles(profileFile));
	                    profileFileLastModified = profileFile.lastModified();
	                }
	            }
	        }
    	}

        credentialProviderCache.clear();
    }

    public Map<String, BasicProfile> getAllBasicProfiles() {
    	
    	Map<String, BasicProfile> profilesMap = new HashMap<String, BasicProfile>();
    	
    	for(AllProfiles allProfile : allProfiles.values()) {
    		profilesMap.putAll(allProfile.getProfiles());
    	}

        return profilesMap;
    }

    private static File getCredentialProfilesFile() {
        return AwsProfileFileLocationProvider.DEFAULT_CREDENTIALS_LOCATION_PROVIDER.getLocation();
    }
    
    private static File getConfigProfilesFile() {
        return AwsProfileFileLocationProvider.DEFAULT_CONFIG_LOCATION_PROVIDER.getLocation();
    }

    private static AllProfiles loadProfiles(File file) {
        return BasicProfileConfigLoader.INSTANCE.loadProfiles(file);
    }

    private AWSCredentialsProvider fromProfile(BasicProfile profile) {
        if (profile.isRoleBasedProfile()) {
            return new ProfileAssumeRoleCredentialsProvider(profileCredentialsService, getMergedAllProfiles(),
                                                            profile);
        } else if (profile.isProcessBasedProfile()) {
            return new ProfileProcessCredentialsProvider(profile);
        } else {
            return new ProfileStaticCredentialsProvider(profile);
        }
    }

}
