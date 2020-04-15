# Using command line to deploy the sample

Easiest way to deploy this sample is using cmdline, you can use **PowerShell** and [AWS Command Line Interface](https://aws.amazon.com/cli/) on **Windows** or **wget** and **AWS Command Line Interface** on **macOS**.

## On this Page
- [Create Directory](#create-directory)
- [Download Sample](#download-sample)
- [Deploy Sample](#deploy-sample)

## Create Directory

Create a directory to download the sample
```bat
mkdir audiodemo
cd audiodemo
```

## Download Sample

Download the sample and CloudFormation template

**Windows**

Using powershell **invoke-webrequest** download the sample and CloudFormation template

```powershell
Invoke-WebRequest https://raw.githubusercontent.com/aws-samples/amazon-chime-voiceconnector-transcription/master/infrastructure/deployment-template.json -OutFile deployment-template.json
```
```powershell
Invoke-WebRequest https://github.com/aws-samples/amazon-chime-voiceconnector-transcription/releases/download/v0.11.0/amazon-chime-voiceconnector-recordandtranscribe.zip -OutFile amazon-chime-voiceconnector-recordandtranscribe.zip
```

**macOS**

Using **wget** download the sample and CloudFormation template

```
wget https://raw.githubusercontent.com/aws-samples/amazon-chime-voiceconnector-transcription/master/infrastructure/deployment-template.json
```
```
wget https://github.com/aws-samples/amazon-chime-voiceconnector-transcription/releases/download/v0.11.0/amazon-chime-voiceconnector-recordandtranscribe.zip
```
> Missing Wget? Install using [brew](https://brew.sh/)
>```
>brew install wget
>```
>> Missing brew?
>>```ruby
>>/usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
>>```

## Deploy Sample
Make sure you are in the infrastructure directory which includes `deployment-template.json`.

Use [AWS Command Line Interface](https://aws.amazon.com/cli/) to deploy the sample

Configure AWS Command Line Interface
```
aws configure
```

Create S3 bucket to upload the lambda code
```
aws s3api create-bucket --bucket source-us-east-1-<accountid> --region us-east-1
```

> NOTE: For ECS solution, parameters for image cleanup are tunable. See [Automated Task and Image Cleanup](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/automated_image_cleanup.html).

Package local artifacts. Lambda function is to receive CloudWatch event and send runTask request to ECS.
```
aws cloudformation package --template-file ./deployment-template.json --s3-bucket source-us-east-1-<accountid> --force-upload --use-json --output-template-file packaged.json
```

Deploy the package. If you choose to use Lambda for transcription solution, specify `SolutionType` as `LAMBDA`. If you choose ECS, specify it as `ECS`. 

> Deploy the package using Lambda solution
>```
>aws cloudformation deploy --template-file ./packaged.json --stack-name CallAudioDemo --capabilities CAPABILITY_IAM --region us-east-1 --parameter-overrides SolutionType=LAMBDA
>```

> Deploy the package using AWS ECS
>```
>ACCOUNT=<replace_with_account>
>REPOSITORY_NAME=chime-transcribe
>IMAGE_NAME=$ACCOUNT.dkr.ecr.us-east-1.amazonaws.com/$REPOSITORY_NAME\:latest
>aws cloudformation deploy --template-file ./packaged.json --stack-name CallAudioDemo --capabilities CAPABILITY_IAM --region us-east-1 --parameter-overrides SolutionType=ECS DockerImage=$IMAGE_NAME KeyPairName=<replace_with_key>
>```

## Deploy Docker Image (ECS specific)
Before image deployment. Make sure you are in the project root directory which includes `build.gradle`, `Dockerfile` and `gradlew` files.

Build docker image
```
./gradlew buildDockerImage
```
> Missing Gradle? Check [Gradle Build Tool](https://gradle.org)

Docker log into ECR
```
aws ecr get-login-password  --region us-east-1 | docker login -u AWS --password-stdin https://$ACCOUNT.dkr.ecr.us-east-1.amazonaws.com 
```

Tag the image and push Docker Image to ECR
```
docker tag chime-transcribe:latest $IMAGE_NAME && docker push $IMAGE_NAME
```
> Missing docker?
> Check [docker install document](https://docs.docker.com/install/)