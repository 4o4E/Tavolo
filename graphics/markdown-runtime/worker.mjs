import {createInterface} from 'node:readline';
import {fileURLToPath, pathToFileURL} from 'node:url';
import path from 'node:path';
import puppeteer from 'puppeteer';
import {renderMermaid} from '@mermaid-js/mermaid-cli';
import {mathjax} from 'mathjax-full/js/mathjax.js';
import {TeX} from 'mathjax-full/js/input/tex.js';
import {SVG} from 'mathjax-full/js/output/svg.js';
import {liteAdaptor} from 'mathjax-full/js/adaptors/liteAdaptor.js';
import {RegisterHTMLHandler} from 'mathjax-full/js/handlers/html.js';
import {AllPackages} from 'mathjax-full/js/input/tex/AllPackages.js';
import {Loader} from 'mathjax-full/js/components/loader.js';
import 'mathjax-full/js/input/tex/require/RequireConfiguration.js';
import hljs from 'highlight.js';

// stdout 专用于逐行 JSON 协议，库诊断写 stderr。
console.log = (...args) => console.error(...args);
const adaptor = liteAdaptor();
RegisterHTMLHandler(adaptor);
const mathPackages = AllPackages.filter(name => !['noundefined', 'noerrors'].includes(name));
const requirePackages = [...mathPackages, 'physics', 'colorv2'];
// 使用 MathJax 自身的 require 机制，但只允许已经随发行包安装、导入的扩展。
Loader.preLoad(...requirePackages.map(name => `[tex]/${name}`));
let browser;
const cliPage = pathToFileURL(path.resolve(path.dirname(fileURLToPath(import.meta.resolve('@mermaid-js/mermaid-cli'))), '../dist/index.html')).href;
const escape = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const inkElements = new Set(['path', 'rect', 'circle', 'ellipse', 'line', 'polyline', 'polygon', 'text']);

// SVG 的零宽描边表示不绘制；Skia 会把它当作设备细线。只处理实际绘图节点，
// 不清除祖先的 stroke 颜色，以保留 cancel、enclose 等显式指定正描边宽度的图形。
function normalizeMathStrokes(node, inheritedWidth = 1) {
  if (adaptor.kind(node) === '#text') return;
  const attribute = adaptor.getAttribute(node, 'stroke-width');
  const width = attribute === undefined ? inheritedWidth : parseFloat(attribute);
  if (width === 0 && inkElements.has(adaptor.kind(node))) adaptor.setAttribute(node, 'stroke', 'none');
  for (const child of adaptor.childNodes(node)) normalizeMathStrokes(child, width);
}

function math(request) {
  // 每个公式隔离宏定义，避免共享 worker 把上一份文档的宏带入下一份文档。
  const tex = new TeX({
    packages: [...mathPackages, 'require'],
    require: {defaultAllow: false, allow: Object.fromEntries(requirePackages.map(name => [name, true]))},
    formatError: (_jax, error) => { throw error; }
  });
  const output = new SVG({fontCache: 'none'});
  const document = mathjax.document('', {InputJax: tex, OutputJax: output});
  const ex = request.fontXHeight ?? request.fontSize * output.font.params.x_height;
  if (!(ex > 0 && Number.isFinite(ex))) throw new Error('公式正文字体的 x-height 必须为有限正数');
  const container = document.convert(request.source, {display: request.display, em: request.fontSize, ex});
  const svg = adaptor.firstChild(container);
  const style = adaptor.getAttribute(svg, 'style') || '';
  // 带公式编号的 SVG 使用百分比宽度和嵌套视口；保留其结构，按引擎给出的最小宽度定尺寸。
  const widthAttribute = adaptor.getAttribute(svg, 'width');
  const width = widthAttribute === '100%'
    ? Number(style.match(/min-width:\s*([\d.]+)ex/)?.[1]) * ex
    : parseFloat(widthAttribute) * ex;
  const height = parseFloat(adaptor.getAttribute(svg, 'height')) * ex;
  const descent = -Number(style.match(/vertical-align:\s*(-?[\d.]+)ex/)?.[1] || 0) * ex;
  if (!(width > 0 && height > 0)) throw new Error('公式没有可用的 SVG 尺寸');
  adaptor.setAttribute(svg, 'width', String(width));
  adaptor.setAttribute(svg, 'height', String(height));
  adaptor.setAttribute(svg, 'color', request.color);
  adaptor.removeAttribute(svg, 'style');
  normalizeMathStrokes(svg);
  const xml = adaptor.outerHTML(svg).replaceAll('currentColor', request.color);
  return {data: Buffer.from(xml).toString('base64'), width, height, baseline: height - descent, svg: true};
}

async function mermaid(request) {
  browser ??= await puppeteer.launch({headless: true});
  const denied = new Set();
  const resourceError = cause => new Error(`Mermaid 包含未经解析的外部资源: ${[...denied].join(', ')}`, {cause});
  // 只允许适配器自身的安装资源；文档不能借 Mermaid 访问网络或本机文件。
  const restrictedBrowser = {
    async newPage() {
      const page = await browser.newPage();
      await page.setRequestInterception(true);
      page.on('request', event => {
        const url = event.url();
        if (url === cliPage || url.startsWith('https://mermaid-cli-intercept.invalid/') || url.startsWith('data:')) {
          return event.continue({}, -10);
        }
        denied.add(url);
        return event.abort('blockedbyclient', 10);
      });
      return page;
    }
  };
  const {data} = await renderMermaid(restrictedBrowser, request.source, 'png', {
    viewport: {width: Math.ceil(request.width), height: 800, deviceScaleFactor: 2},
    backgroundColor: 'transparent',
    fontEmbed: false,
    mermaidConfig: {
      startOnLoad: false,
      securityLevel: 'strict',
      theme: request.dark ? 'dark' : 'default',
      // classic 使用实色描边；默认外观的渐变描边会让同一节点不同边呈现不同颜色。
      look: 'classic',
      fontFamily: request.fontFamily,
      secure: ['securityLevel', 'startOnLoad', 'fontFamily'],
    }
  }).catch(error => {
    if (denied.size) throw resourceError(error);
    throw error;
  });
  if (denied.size) throw resourceError();
  const bytes = Buffer.from(data);
  const width = bytes.readUInt32BE(16) / 2;
  const height = bytes.readUInt32BE(20) / 2;
  return {data: bytes.toString('base64'), width, height, baseline: height, svg: false};
}

async function dispatch(request) {
  switch (request.operation) {
    case 'math': return math(request);
    case 'mermaid': return mermaid(request);
    case 'highlight': return {html: hljs.getLanguage(request.language)
      ? hljs.highlight(request.source, {language: request.language, ignoreIllegals: true}).value
      : escape(request.source)};
    default: throw new Error(`未知 Markdown 引擎操作: ${request.operation}`);
  }
}

try {
  for await (const line of createInterface({input: process.stdin, crlfDelay: Infinity})) {
    let response;
    try { response = await dispatch(JSON.parse(line)); }
    catch (error) { response = {error: error.message}; }
    process.stdout.write(JSON.stringify(response) + '\n');
  }
} finally {
  if (browser) await browser.close();
}
