package com.ubhave.datastore.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.util.Log;

import com.ubhave.dataformatter.DataFormatter;
import com.ubhave.dataformatter.json.JSONFormatter;
import com.ubhave.datahandler.config.DataHandlerConfig;
import com.ubhave.datahandler.config.DataStorageConfig;
import com.ubhave.datahandler.config.DataStorageConstants;
import com.ubhave.datahandler.except.DataHandlerException;
import com.ubhave.datastore.DataStorageInterface;
import com.ubhave.sensormanager.ESException;
import com.ubhave.sensormanager.data.SensorData;
import com.ubhave.sensormanager.sensors.SensorUtils;

public class FileDataStorage implements DataStorageInterface
{	
	private final Context context;
	private final DataHandlerConfig config;
	private final FileStoreWriter logFileStore;
	private final FileStoreCleaner fileStoreCleaner;
	private static HashMap<String, Object> lockMap = new HashMap<String, Object>();

	public FileDataStorage(final Context context, final Object fileTransferLock)
	{
		this.context = context;
		this.config = DataHandlerConfig.getInstance();
		this.fileStoreCleaner = new FileStoreCleaner(fileTransferLock);
		this.logFileStore = new FileStoreWriter(fileStoreCleaner, lockMap);
	}
	
	@Override
	public void onDataUploaded()
	{}

	@Override
	public String prepareDataForUpload()
	{
		try
		{
			final String rootPath = (String) config.get(DataStorageConfig.LOCAL_STORAGE_ROOT_NAME);
			String uploadDirectory = (String) config.get(DataStorageConfig.LOCAL_STORAGE_UPLOAD_DIRECTORY_NAME);
			File[] rootDirectory = (new File(rootPath)).listFiles();
			int counter = 0;
			if (rootDirectory != null)
			{
				for (File directory : rootDirectory)
				{
					if (directory != null && directory.isDirectory())
					{
						String directoryName = directory.getName();
						if (directoryName != null && !directoryName.contains(uploadDirectory))
						{
							counter ++;
							synchronized (getLock(directoryName))
							{
								try
								{
									fileStoreCleaner.moveDirectoryContentsForUpload(directory.getAbsolutePath());
								}
								catch (Exception e)
								{
									e.printStackTrace();
								}
							}
						}
					}
				}
			}
			if (DataHandlerConfig.shouldLog())
			{
				Log.d("DataManager", "Moved "+counter+" directories.");
			}
			
			uploadDirectory = (String) config.get(DataStorageConfig.LOCAL_STORAGE_UPLOAD_DIRECTORY_PATH);
			return new File(uploadDirectory).getAbsolutePath();
		}
		catch (DataHandlerException e)
		{
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public List<SensorData> getRecentSensorData(int sensorId, long startTimestamp) throws ESException, IOException
	{
		ArrayList<SensorData> outputList = new ArrayList<SensorData>();
		try
		{
			String sensorName = SensorUtils.getSensorName(sensorId);
			String rootPath = (String) config.get(DataStorageConfig.LOCAL_STORAGE_ROOT_NAME);
			JSONFormatter jsonFormatter = JSONFormatter.getJSONFormatter(context, sensorId);
			synchronized (getLock(sensorName))
			{
				String directoryFullPath = rootPath + "/" + sensorName;
				File dir = new File(directoryFullPath);
				File[] files = dir.listFiles();
				if (files != null)
				{
					for (File file : files)
					{
						String line;
						BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
						while ((line = br.readLine()) != null)
						{
							try
							{
								// convert json string to sensor data object
								long timestamp = jsonFormatter.getTimestamp(line);
								if (timestamp >= startTimestamp)
								{
									SensorData sensorData = jsonFormatter.toSensorData(line);
									if (sensorData.getTimestamp() >= startTimestamp)
									{
										outputList.add(sensorData);
									}
								}
							}
							catch (Exception e)
							{
								e.printStackTrace();
							}
						}
						br.close();
					}
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return outputList;
	}

	private Object getLock(String key)
	{
		Object lock;
		synchronized (lockMap)
		{
			if (lockMap.containsKey(key))
			{
				lock = lockMap.get(key);
			}
			else
			{
				lock = new Object();
				lockMap.put(key, lock);
			}
		}
		return lock;
	}

	@Override
	public void logSensorData(final SensorData data, final DataFormatter formatter) throws DataHandlerException
	{
		String sensorName;
		try
		{
			sensorName = SensorUtils.getSensorName(data.getSensorType());
		}
		catch (ESException e)
		{
			sensorName = DataStorageConstants.UNKNOWN_SENSOR;
		}
		String directoryName = sensorName;
		logFileStore.writeData(directoryName, formatter.toString(data));
	}

	@Override
	public void logExtra(final String tag, final String data) throws DataHandlerException
	{
		logFileStore.writeData(tag, data);
	}
}
