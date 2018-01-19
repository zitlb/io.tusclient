# io.tusclient
java TusClient with Random Access File


This is the modified version of https://github.com/tus/tus-java-client using RandomAccessFile. 

Sample Code.. 


		final TusClient client = new TusClient();
		client.setBaseURL(new URL("http://localhost:1080/files/"));
		TusURLStore store = PropertiesStore.getStore();		
		client.setUrlStore(store);
		client.setResumingEnabled(true);
		File file = new File("/tmp/testfile");
		final TusFileStream upload = new TusFileStream(file);

		System.out.println("Starting upload...");

		TusExecutor executor = new TusExecutor() {
		    @Override
		    protected void makeAttempt() throws TusProtocolException, IOException {
		        TusUploader uploader = client.resumeOrCreateUpload(upload);
		        uploader.setChunkSize(4*1024*1024);
		        do {
		            long totalBytes = upload.getSize();
		            long bytesUploaded = uploader.getOffset();
		            double progress = (double) bytesUploaded / totalBytes * 100;
		            System.out.printf("Upload at %06.2f%%.\n", progress);
		        } while(uploader.uploadChunk() > -1);
		        uploader.finish();
		        System.out.println("Upload finished.");
		        System.out.format("Upload available at: %s", uploader.getUploadURL().toString());
		    }
		};
		executor.makeAttempts();
