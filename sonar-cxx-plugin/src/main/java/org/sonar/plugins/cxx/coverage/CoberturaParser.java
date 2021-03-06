/*
 * Sonar C++ Plugin (Community)
 * Copyright (C) 2010-2016 SonarOpenCommunity
 * http://github.com/SonarOpenCommunity/sonar-cxx
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.cxx.coverage;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.codehaus.staxmate.in.SMHierarchicCursor;
import org.codehaus.staxmate.in.SMInputCursor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugins.cxx.utils.CxxUtils;
import org.sonar.plugins.cxx.utils.StaxParser;

/**
 * {@inheritDoc}
 */
public class CoberturaParser extends CxxCoverageParser {
  public static final Logger LOG = Loggers.get(CoberturaParser.class);
  public CoberturaParser() {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processReport(final SensorContext context, File report, final Map<String, CoverageMeasures> coverageData)
    throws XMLStreamException {
    LOG.debug("Parsing 'Cobertura' format");
    StaxParser parser = new StaxParser(new StaxParser.XmlStreamHandler() {
      /**
       * {@inheritDoc}
       */
      @Override
      public void stream(SMHierarchicCursor rootCursor) throws XMLStreamException {
        rootCursor.advance();
        collectPackageMeasures(context, rootCursor.descendantElementCursor("package"), coverageData);
      }
    });
    parser.parse(report);
  }

  private void collectPackageMeasures(final SensorContext context, SMInputCursor pack, Map<String, CoverageMeasures> coverageData)
    throws XMLStreamException {
    while (pack.getNext() != null) {
      collectFileMeasures(context, pack.descendantElementCursor("class"), coverageData);
    }
  }

  private void collectFileMeasures(final SensorContext context, SMInputCursor clazz, Map<String, CoverageMeasures> coverageData)
    throws XMLStreamException {
    while (clazz.getNext() != null) {
      String normalPath = CxxUtils.normalizePathFull(clazz.getAttrValue("filename"), context.fileSystem().baseDir().getAbsolutePath());
      if (normalPath != null) {
        CoverageMeasures builder = coverageData.get(normalPath);
        if (builder == null) {
          builder = CoverageMeasures.create();
          coverageData.put(normalPath, builder);
        }
        collectFileData(clazz, builder);
      }
    }
  }

  private void collectFileData(SMInputCursor clazz, CoverageMeasures builder) throws XMLStreamException {
    SMInputCursor line = clazz.childElementCursor("lines").advance().childElementCursor("line");
    while (line.getNext() != null) {
      int lineId = Integer.parseInt(line.getAttrValue("number"));
      long noHits = Long.parseLong(line.getAttrValue("hits"));
      if (noHits > Integer.MAX_VALUE) {
        LOG.warn("Truncating the actual number of hits ({}) to the maximum number supported by Sonar ({})",
          noHits, Integer.MAX_VALUE);
        noHits = Integer.MAX_VALUE;
      }
      builder.setHits(lineId, (int) noHits);

      String isBranch = line.getAttrValue("branch");
      String text = line.getAttrValue("condition-coverage");
      if ("true".equals(isBranch) && text != null && !text.trim().isEmpty()) {
        Pattern p = Pattern.compile("\\((.*?)\\)");
        Matcher m = p.matcher(text);
        if (m.find()) {
          String[] conditions = m.group(1).split("/");
          builder.setConditions(lineId, Integer.parseInt(conditions[1]), Integer.parseInt(conditions[0]));
        }
      }
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
