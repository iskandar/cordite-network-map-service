import React from 'react';
import Default from 'containers/Default/Default';
import {Login} from 'containers/Login/Login'
import {checkAuth, login} from 'scripts/restCalls';
import {LogoutModal} from 'components/LogoutModal/LogoutModal';

export default class App extends React.Component {
  constructor(props) {
    super(props)
    this.state = {
      status: 'await',
      subDomain: 'default',
      style: ''
    }

    this.NMSLogin = this.NMSLogin.bind(this);
    this.isAuthorised = this.isAuthorised.bind(this);
    this.toggleModal = this.toggleModal.bind(this);
  }

  componentDidMount(){
    this.isAuthorised();
  }

  NMSLogin(loginData){
    return login(loginData)
    .then( () => this.isAuthorised() )
    .then( status => {
      if(status != 200)  return "fail";
      return "success";
    })
    .catch( err => console.log(err) )
  }

  toggleModal(e){
    this.state.style ? this.setState({style: ''}) : this.setState({style: 'on'});
  }

  isAuthorised(){
    return checkAuth()
    .then(status => {
      if(status == 200){
        this.setState({
          status: 'done',
          subDomain: 'default',
        })
      }
      else{
        this.setState({
          status: 'done',
          subDomain: 'login',
        })
      }
      return status;
    })
    .catch(err => console.log(err));
  }

  render(){
    let page = null;
    switch(this.state.subDomain){
      case 'login':
        page = <Login nmsLogin={this.NMSLogin} />;
        break;
      case 'default':
        page = <Default toggleModal={this.toggleModal} />;
        break;
      default: 
        page = <Login nmsLogin={this.nmsLogin} />;
        break;
    }
    
    return (
      <div className='app-component'>
        { (this.state.status == 'done') ? page :  "" }
        <LogoutModal 
          toggleModal={this.toggleModal}
          style={this.state.style} 
          isAuthorised={this.isAuthorised} />
      </div>    
    );
  }
}