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
Invoke-WebRequest https://github.com/aws-samples/amazon-chime-voiceconnector-transcription/releases/download/v0.10.0/amazon-chime-voiceconnector-recordandtranscribe.zip -OutFile amazon-chime-voiceconnector-recordandtranscribe.zip
```

**macOS**

Using **wget** download the sample and CloudFormation template

```
wget https://raw.githubusercontent.com/aws-samples/amazon-chime-voiceconnector-transcription/master/infrastructure/deployment-template.json
```
```
wget https://github.com/aws-samples/amazon-chime-voiceconnector-transcription/releases/download/v0.10.0/amazon-chime-voiceconnector-recordandtranscribe.zip
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

Use [AWS Command Line Interface](https://aws.amazon.com/cli/) to deploy the sample

Configure AWS Command Line Interface
```
aws configure
```

Create S3 bucket to upload the lambda code
```
aws s3api create-bucket --bucket source-us-east-1-<accountid> --region us-east-1
```

Package local artifacts
```
aws cloudformation package --template-file ./deployment-template.json --s3-bucket source-us-east-1-<accountid> --force-upload --use-json --output-template-file packaged.json
```

Deploy the package

```
aws cloudformation deploy --template-file ./packaged.json --stack-name CallAudioDemo --capabilities CAPABILITY_IAM --region us-east-1
```
