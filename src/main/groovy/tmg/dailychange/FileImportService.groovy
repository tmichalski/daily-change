package tmg.dailychange

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import org.apache.commons.io.FilenameUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

import java.text.SimpleDateFormat

@Service
class FileImportService {
    private static final Logger LOG = LoggerFactory.getLogger(FileImportService.class)

    private static final int ITEM = 1
    private static final int DESCRIPTION = 2
    private static final int ON_HAND = 3
    private static final int ON_ORDER = 4
    private static final int NEXT_PO = 15
    private static final int POD = 23
    private static final int DAILY_RATE = 27
    private static final int BRAND_MANAGER = 29

    private static final Map headers = [
        'ITEM': ITEM,
        'DESCRIPTION': DESCRIPTION,
        'ON HAND': ON_HAND,
        'ON ORDER': ON_ORDER,
        'NEXT PO #': NEXT_PO,
        'POINTS OF DIST': POD,
        'DAILY RATE': DAILY_RATE,
        'BRAND MANAGER': BRAND_MANAGER
    ]

    @Value('${report.path}')
    private String reportPath

    @Value('${report.filename}')
    private String reportFilename

    @Value('${report.filename.ext}')
    private String reportFilenameExt

    @Autowired
    FileImportService() {
        
    }

    void load() {
        if (LOG.debugEnabled) LOG.debug("Inspecting folder: ${reportPath}")

        Map items = [:]

        new File(reportPath).listFiles().sort{ it.name}.each { file ->
            String filename = file.name.toLowerCase()
            if (LOG.debugEnabled) LOG.debug("Found file: ${file.name}")

            String date = FilenameUtils.getBaseName(file.name)

            if (filename.endsWith('.csv')) {
                LOG.info("Inspecting ${filename}")

                CSVReader csvReader = new CSVReader(new FileReader(file))
                String [] fileRow = csvReader.readNext()

                boolean isHeadersFound = true
                headers.keySet().each {
                    isHeadersFound = fileRow.contains(it)
                }

                if (!isHeadersFound) LOG.warn("$filename is missing one of these columns: $headers")

                while ((fileRow = csvReader.readNext()) != null) {
                    Map itemRow = [
                        'DATE': date,
                        'DESCRIPTION': fileRow[DESCRIPTION],
                        'ITEM': fileRow[ITEM],
                        'ON HAND': fileRow[ON_HAND],
                        'ON ORDER': fileRow[ON_ORDER],
                        'NEXT PO #': fileRow[NEXT_PO],
                        'POINTS OF DIST': fileRow[POD],
                        'DAILY RATE': fileRow[DAILY_RATE],
                        'ON HAND DELTA': 0,
                        'ON HAND SURPRISE': '',
                        'ON HAND UP w/o PO': '',
                        'ON ORDER DELTA': 0,
                        'ON ORDER LOST': '',
                        'POINTS OF DIST DELTA': 0,
                        'POINTS OF DIST > 10': '',
                        'BRAND MANAGER': fileRow[BRAND_MANAGER]
                    ]

                    Collection itemRows = (Collection)items[itemRow['ITEM']]
                    if (!itemRows) {
                        itemRows = []
                        items[itemRow['ITEM']] = itemRows
                    }

                    if (itemRows) {
                        Map lastRow = itemRows.last()
                        if (lastRow) {
                            itemRow['ON HAND DELTA'] = itemRow['ON HAND'].toBigDecimal() - lastRow['ON HAND'].toBigDecimal()
                            itemRow['ON ORDER DELTA'] = itemRow['ON ORDER'].toBigDecimal() - lastRow['ON ORDER'].toBigDecimal()
                            itemRow['POINTS OF DIST DELTA'] = itemRow['POINTS OF DIST'].toBigDecimal() - lastRow['POINTS OF DIST'].toBigDecimal()

                            // ON HAND UP w/o PO
                            if (itemRow['ON HAND DELTA'] > 0 && !lastRow['NEXT PO #']) {
                                itemRow['ON HAND UP w/o PO'] = 'X'
                            }

                            // ON HAND SURPRISE
                            if (itemRow['ON ORDER DELTA'] == 0 && itemRow['ON HAND DELTA'] > 0) {
                                itemRow['ON HAND SURPRISE'] = 'X'
                            }

                            // ON ORDER LOST
                            if (itemRow['ON ORDER DELTA'] < 0 && itemRow['ON HAND DELTA'] <= 0) {
                                itemRow['ON ORDER LOST'] = 'X'
                            }

                            // POINTS OF DIST > 10
                            if (itemRow['POINTS OF DIST DELTA'] >= 10) {
                                itemRow['POINTS OF DIST > 10'] = 'X'
                            }
                        }
                    }
                    itemRows.add(itemRow)
                }

            } else {
                if (LOG.debugEnabled) LOG.debug("Skipping ${file.name} since it doesn't end in .csv")
            }
        }

        // Write to file
        SimpleDateFormat formatter = new SimpleDateFormat('MM-dd-yyyy hh-mm-ss')
        String reportFilenameFull = reportFilename + ' ' + formatter.format(new Date()) + '.' + reportFilenameExt

        CSVWriter writer = new CSVWriter(new FileWriter(reportFilenameFull))
        String[] headerRow = [
                'DATE',
                'ITEM',
                'DESCRIPTION',
                'ON HAND',
                'ON HAND DELTA',
                'ON HAND SURPRISE',
                'ON HAND UP w/o PO',
                'ON ORDER',
                'ON ORDER DELTA',
                'ON ORDER LOST',
                'NEXT PO #',
                'POINTS OF DIST',
                'POINTS OF DIST DELTA',
                'POINTS OF DIST > 10',
                'DAILY RATE',
                'BRAND MANAGER'
        ]
        writer.writeNext(headerRow)

        items.values().each { itemRows ->
            itemRows.each { itemRow ->
                String[] out = [
                        itemRow['DATE'],
                        itemRow['ITEM'],
                        itemRow['DESCRIPTION'],
                        itemRow['ON HAND'],
                        itemRow['ON HAND DELTA'],
                        itemRow['ON HAND SURPRISE'],
                        itemRow['ON HAND UP w/o PO'],
                        itemRow['ON ORDER'],
                        itemRow['ON ORDER DELTA'],
                        itemRow['ON ORDER LOST'],
                        itemRow['NEXT PO #'],
                        itemRow['POINTS OF DIST'],
                        itemRow['POINTS OF DIST DELTA'],
                        itemRow['POINTS OF DIST > 10'],
                        itemRow['DAILY RATE'],
                        itemRow['BRAND MANAGER']
                ]
                writer.writeNext(out)
            }
        }
        writer.close()

        if (LOG.debugEnabled) LOG.debug("File import completed.")
    }

}
