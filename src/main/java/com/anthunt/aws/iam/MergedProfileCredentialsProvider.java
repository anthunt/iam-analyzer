package com.anthunt.aws.iam;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;

public class MergedProfileCredentialsProvider extends ProfileCredentialsProvider {

	public MergedProfileCredentialsProvider() {
		super(new SharedProfileConfigFile(), null);
	}

	public MergedProfileCredentialsProvider(String profileName) {
		super(new SharedProfileConfigFile(), profileName);
	}

}
