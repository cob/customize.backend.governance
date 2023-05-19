module.exports = {
  entry: './index',
  output: {
    filename: 'browser-bundle.js',
    path: __dirname +'/build'
  },
  //devtool: 'source-map',
  externals: {
        // require("jquery") is external and available
        //  on the global var jQuery
        "jquery": "jQuery",
        "marked": "marked"
  },
  module: {
    loaders: [
      {
        test: /\.js$/,
        loader: 'babel-loader',
        query: {
          presets: ['env','es2015' ,'react']
        }
      }
    ]
  }
};
